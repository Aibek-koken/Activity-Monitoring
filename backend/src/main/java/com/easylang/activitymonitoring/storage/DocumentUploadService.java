package com.easylang.activitymonitoring.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentUploadService {

    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final int MAX_ZIP_ENTRIES = 10_000;
    private static final Pattern START_XREF = Pattern.compile("startxref\\s+(\\d+)\\s+%%EOF\\s*$");

    private final PrivateDocumentStorageService storage;
    private final long maxSizeBytes;

    public DocumentUploadService(
            PrivateDocumentStorageService storage,
            @Value("${app.document-storage.max-size-bytes:20971520}") long maxSizeBytes
    ) {
        if (maxSizeBytes <= 0 || maxSizeBytes >= Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Document upload size limit must be between 1 and 2147483646 bytes");
        }
        this.storage = storage;
        this.maxSizeBytes = maxSizeBytes;
    }

    public UploadedDocument validateAndStore(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalid("A PDF or DOCX file is required");
        }

        String originalName = safeOriginalName(file.getOriginalFilename());
        String extension = extension(originalName);
        if (file.getSize() > maxSizeBytes) {
            throw invalid("The document exceeds the upload size limit");
        }

        byte[] bytes = readLimited(file);
        String contentType;
        if ("pdf".equals(extension)) {
            validatePdf(bytes);
            contentType = PDF_CONTENT_TYPE;
        } else if ("docx".equals(extension)) {
            validateDocx(bytes);
            contentType = DOCX_CONTENT_TYPE;
        } else {
            throw invalid("Only PDF and DOCX files are accepted");
        }

        StoredDocument stored = storage.store(new ByteArrayInputStream(bytes));
        compensateOnRollback(stored.storageKey());
        return new UploadedDocument(
                stored.storageKey(), originalName, contentType, stored.sizeBytes(), stored.sha256()
        );
    }

    public void discard(String storageKey) {
        storage.deleteIfExists(storageKey);
    }

    private String safeOriginalName(String originalName) {
        if (originalName == null || originalName.isBlank()
                || !originalName.equals(originalName.trim())
                || originalName.length() > 255
                || originalName.equals(".") || originalName.equals("..")
                || originalName.indexOf('/') >= 0 || originalName.indexOf('\\') >= 0) {
            throw invalid("The document filename is unsafe");
        }
        for (int index = 0; index < originalName.length(); index++) {
            if (Character.isISOControl(originalName.charAt(index))) {
                throw invalid("The document filename is unsafe");
            }
        }
        return originalName;
    }

    private String extension(String originalName) {
        int separator = originalName.lastIndexOf('.');
        if (separator <= 0 || separator == originalName.length() - 1) {
            return "";
        }
        return originalName.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private byte[] readLimited(MultipartFile file) {
        try (InputStream input = file.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxSizeBytes) {
                    throw invalid("The document exceeds the upload size limit");
                }
                output.write(buffer, 0, read);
            }
            if (total == 0) {
                throw invalid("A PDF or DOCX file is required");
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw invalid("The uploaded document could not be read");
        }
    }

    private void validatePdf(byte[] bytes) {
        int headerLimit = Math.min(bytes.length - 4, 1024);
        boolean headerFound = false;
        for (int index = 0; index < headerLimit; index++) {
            if (bytes[index] == '%' && bytes[index + 1] == 'P' && bytes[index + 2] == 'D'
                    && bytes[index + 3] == 'F' && bytes[index + 4] == '-') {
                headerFound = true;
                break;
            }
        }
        String full = new String(bytes, StandardCharsets.ISO_8859_1);
        String tail = new String(bytes, Math.max(0, bytes.length - 4096), Math.min(bytes.length, 4096),
                StandardCharsets.ISO_8859_1);
        Matcher startXref = START_XREF.matcher(tail);
        if (!headerFound || !startXref.find() || !full.contains(" obj")
                || !full.contains("endobj") || !full.contains("/Root")) {
            throw invalid("The uploaded file is not a valid PDF");
        }
        try {
            int xrefOffset = Integer.parseInt(startXref.group(1));
            if (xrefOffset < 0 || xrefOffset >= bytes.length) {
                throw invalid("The uploaded file is not a valid PDF");
            }
            String xrefTarget = full.substring(xrefOffset, Math.min(full.length(), xrefOffset + 120)).stripLeading();
            boolean traditionalXref = xrefTarget.startsWith("xref");
            boolean xrefStream = xrefTarget.matches("(?s)^\\d+\\s+\\d+\\s+obj.*")
                    && full.contains("/Type /XRef");
            if (!traditionalXref && !xrefStream) {
                throw invalid("The uploaded file is not a valid PDF");
            }
        } catch (NumberFormatException exception) {
            throw invalid("The uploaded file is not a valid PDF");
        }
    }

    private void validateDocx(byte[] bytes) {
        boolean contentTypesFound = false;
        boolean documentFound = false;
        long expandedBytes = 0;
        long expandedLimit = Math.max(maxSizeBytes * 5, 100L * 1024 * 1024);
        int entries = 0;

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_ZIP_ENTRIES || unsafeZipEntry(entry.getName())) {
                    throw invalid("The DOCX archive is unsafe");
                }
                if (entry.getName().equalsIgnoreCase("word/vbaProject.bin")) {
                    throw invalid("Macro-enabled documents are not accepted");
                }

                ByteArrayOutputStream contentTypes = entry.getName().equals("[Content_Types].xml")
                        ? new ByteArrayOutputStream() : null;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expandedBytes += read;
                    if (expandedBytes > expandedLimit) {
                        throw invalid("The DOCX archive expands beyond the safe limit");
                    }
                    if (contentTypes != null) {
                        if (contentTypes.size() + read > 1024 * 1024) {
                            throw invalid("The DOCX content-types manifest is too large");
                        }
                        contentTypes.write(buffer, 0, read);
                    }
                }
                if (contentTypes != null) {
                    String xml = contentTypes.toString(StandardCharsets.UTF_8);
                    contentTypesFound = xml.contains(
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"
                    );
                }
                if (entry.getName().equals("word/document.xml")) {
                    documentFound = true;
                }
                zip.closeEntry();
            }
        } catch (IOException exception) {
            throw invalid("The uploaded file is not a valid DOCX");
        }

        if (!contentTypesFound || !documentFound) {
            throw invalid("The uploaded file is not a valid DOCX");
        }
    }

    private boolean unsafeZipEntry(String name) {
        if (name == null || name.isBlank() || name.startsWith("/") || name.startsWith("\\")) {
            return true;
        }
        String normalized = name.replace('\\', '/');
        for (String part : normalized.split("/")) {
            if (part.equals("..")) {
                return true;
            }
        }
        return false;
    }

    private void compensateOnRollback(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            storage.deleteIfExists(storageKey);
            throw new IllegalStateException("Document uploads require an active transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    storage.deleteIfExists(storageKey);
                }
            }
        });
    }

    private ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}

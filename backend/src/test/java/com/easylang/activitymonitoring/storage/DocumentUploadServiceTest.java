package com.easylang.activitymonitoring.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class DocumentUploadServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsForgedExtensionMalformedDocxAndUnsafeFilenameWithoutWritingFiles() throws Exception {
        DocumentUploadService service = serviceWithLimit(1024);

        assertBadRequest(service, new MockMultipartFile(
                "file", "forged.pdf", "application/pdf", "not a PDF".getBytes(StandardCharsets.UTF_8)
        ));
        assertBadRequest(service, new MockMultipartFile(
                "file", "malformed.pdf", "application/pdf",
                "%PDF-1.4\ngarbage\n%%EOF\n".getBytes(StandardCharsets.US_ASCII)
        ));
        assertBadRequest(service, new MockMultipartFile(
                "file", "broken.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                new byte[] {'P', 'K', 3, 4, 0}
        ));
        assertBadRequest(service, new MockMultipartFile(
                "file", "../escape.pdf", "application/pdf",
                "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.US_ASCII)
        ));

        assertStorageEmpty();
    }

    @Test
    void rejectsMacroEnabledDocxAndConfiguredOversizeWithoutWritingFiles() throws Exception {
        assertBadRequest(serviceWithLimit(4096), new MockMultipartFile(
                "file", "macro.docx", "application/octet-stream", docxWithMacro()
        ));
        assertBadRequest(serviceWithLimit(256), new MockMultipartFile(
                "file", "large.pdf", "application/pdf", new byte[257]
        ));

        assertStorageEmpty();
    }

    private DocumentUploadService serviceWithLimit(long limit) {
        return new DocumentUploadService(new PrivateDocumentStorageService(tempDir.toString()), limit);
    }

    private void assertBadRequest(DocumentUploadService service, MockMultipartFile file) {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.validateAndStore(file)
        );
        assertEquals(400, exception.getStatusCode().value());
    }

    private void assertStorageEmpty() throws Exception {
        if (!Files.exists(tempDir)) {
            return;
        }
        try (var paths = Files.list(tempDir)) {
            assertFalse(paths.findAny().isPresent());
        }
    }

    private byte[] docxWithMacro() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            addEntry(zip, "[Content_Types].xml", ("<Types><Override ContentType=\""
                    + "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"
                    + "\"/></Types>").getBytes(StandardCharsets.UTF_8));
            addEntry(zip, "word/document.xml", "<w:document/>".getBytes(StandardCharsets.UTF_8));
            addEntry(zip, "word/vbaProject.bin", new byte[] {1, 2, 3});
        }
        return output.toByteArray();
    }

    private void addEntry(ZipOutputStream zip, String name, byte[] bytes) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }
}

package com.easylang.activitymonitoring.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PrivateDocumentStorageService {

    private final Path root;

    public PrivateDocumentStorageService(
            @Value("${app.document-storage.root:${DOCUMENT_STORAGE_ROOT:./data/review-documents}}") String root
    ) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    public StoredDocument store(InputStream content) {
        if (content == null) {
            throw new IllegalArgumentException("Document content is required");
        }

        Path temporary = null;
        try {
            Files.createDirectories(root);
            temporary = Files.createTempFile(root, ".upload-", ".tmp");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size;
            try (OutputStream file = Files.newOutputStream(
                    temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING
            ); DigestOutputStream output = new DigestOutputStream(file, digest)) {
                size = content.transferTo(output);
            }

            String storageKey = UUID.randomUUID().toString();
            Path destination = resolveKey(storageKey);
            moveWithoutReplacement(temporary, destination);
            temporary = null;
            return new StoredDocument(storageKey, size, HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new DocumentStorageException("Could not store review document", exception);
        } finally {
            deleteTemporary(temporary);
        }
    }

    public InputStream open(String storageKey) {
        Path document = resolveKey(storageKey);
        if (!Files.isRegularFile(document, LinkOption.NOFOLLOW_LINKS)) {
            throw new DocumentStorageException("Stored review document was not found");
        }
        try {
            return Files.newInputStream(document, StandardOpenOption.READ);
        } catch (IOException exception) {
            throw new DocumentStorageException("Could not open review document", exception);
        }
    }

    public void deleteIfExists(String storageKey) {
        Path document = resolveKey(storageKey);
        try {
            Files.deleteIfExists(document);
        } catch (IOException exception) {
            throw new DocumentStorageException("Could not remove uncommitted review document", exception);
        }
    }

    private Path resolveKey(String storageKey) {
        if (storageKey == null || !storageKey.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new IllegalArgumentException("Invalid document storage key");
        }
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.getParent().equals(root)) {
            throw new IllegalArgumentException("Invalid document storage key");
        }
        return resolved;
    }

    private void moveWithoutReplacement(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination);
        }
    }

    private void deleteTemporary(Path temporary) {
        if (temporary == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
            // The original storage failure remains the actionable error.
        }
    }
}

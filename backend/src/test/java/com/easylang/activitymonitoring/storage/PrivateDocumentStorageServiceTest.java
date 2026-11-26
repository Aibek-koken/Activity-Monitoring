package com.easylang.activitymonitoring.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PrivateDocumentStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storesEachUploadUnderANewOpaqueKeyWithoutOverwritingBytes() throws Exception {
        PrivateDocumentStorageService storage = new PrivateDocumentStorageService(tempDir.toString());
        byte[] bytes = "immutable review document".getBytes(StandardCharsets.UTF_8);

        StoredDocument first = storage.store(new ByteArrayInputStream(bytes));
        StoredDocument second = storage.store(new ByteArrayInputStream(bytes));

        assertNotEquals(first.storageKey(), second.storageKey());
        assertEquals(bytes.length, first.sizeBytes());
        assertEquals("9738ef9b2fdd7de309d90a2e607280d645e52642df23c20249f938d9137d82f9",
                first.sha256());
        try (InputStream content = storage.open(first.storageKey())) {
            assertArrayEquals(bytes, content.readAllBytes());
        }
        try (InputStream content = storage.open(second.storageKey())) {
            assertArrayEquals(bytes, content.readAllBytes());
        }
    }

    @Test
    void rejectsKeysThatCouldEscapeThePrivateRoot() {
        PrivateDocumentStorageService storage = new PrivateDocumentStorageService(tempDir.toString());

        assertThrows(IllegalArgumentException.class, () -> storage.open("../outside"));
        assertThrows(IllegalArgumentException.class, () -> storage.deleteIfExists("nested/file"));
    }

    @Test
    void removesTemporaryAndFinalFilesWhenAWriteFails() throws Exception {
        PrivateDocumentStorageService storage = new PrivateDocumentStorageService(tempDir.toString());
        InputStream failing = new InputStream() {
            private int count;

            @Override
            public int read() throws IOException {
                if (count++ < 4) {
                    return 'x';
                }
                throw new IOException("simulated upload failure");
            }
        };

        assertThrows(DocumentStorageException.class, () -> storage.store(failing));
        try (var files = Files.list(tempDir)) {
            assertFalse(files.findAny().isPresent());
        }
    }

    @Test
    void deletesOnlyTheRequestedStoredObjectForTransactionCompensation() throws Exception {
        PrivateDocumentStorageService storage = new PrivateDocumentStorageService(tempDir.toString());
        StoredDocument stored = storage.store(new ByteArrayInputStream(new byte[] {1, 2, 3}));

        storage.deleteIfExists(stored.storageKey());

        assertThrows(DocumentStorageException.class, () -> storage.open(stored.storageKey()));
    }
}

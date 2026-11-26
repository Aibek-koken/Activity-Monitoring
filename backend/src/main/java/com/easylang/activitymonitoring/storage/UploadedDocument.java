package com.easylang.activitymonitoring.storage;

public record UploadedDocument(
        String storageKey,
        String originalName,
        String contentType,
        long sizeBytes,
        String sha256
) {
}

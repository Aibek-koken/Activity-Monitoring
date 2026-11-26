package com.easylang.activitymonitoring.storage;

public record StoredDocument(String storageKey, long sizeBytes, String sha256) {
}

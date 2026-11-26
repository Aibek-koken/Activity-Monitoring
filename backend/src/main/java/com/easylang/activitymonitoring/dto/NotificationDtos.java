package com.easylang.activitymonitoring.dto;

import com.easylang.activitymonitoring.model.NotificationType;
import java.time.Instant;
import java.util.List;

public final class NotificationDtos {
    private NotificationDtos() { }
    public record Item(Long id, NotificationType type, String message, Long activityId,
                       String activityNumber, Instant createdAt, Instant readAt, boolean actionable) { }
    public record Page(List<Item> notifications, int page, int totalPages, long totalElements, long unreadCount) { }
}

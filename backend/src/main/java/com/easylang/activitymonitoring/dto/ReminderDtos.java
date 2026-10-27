package com.easylang.activitymonitoring.dto;

import com.easylang.activitymonitoring.model.NotificationType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ReminderDtos {
    private ReminderDtos() {
    }

    public record ReminderListResponse(List<ReminderResponse> reminders) {
    }

    public record ReminderResponse(
            Long id,
            NotificationType type,
            LocalDate periodDate,
            String message,
            Long activityId,
            String activityNumber,
            Instant createdAt
    ) {
    }
}

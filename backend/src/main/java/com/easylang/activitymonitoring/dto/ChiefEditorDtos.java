package com.easylang.activitymonitoring.dto;

import com.easylang.activitymonitoring.model.ReviewKind;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ChiefEditorDtos {
    private ChiefEditorDtos() {
    }

    public record AssignedProjectListResponse(List<AssignedProjectResponse> projects) {
    }

    public record AssignedProjectResponse(
            Long id,
            String projectName,
            boolean responsible,
            LocalDate createdDate
    ) {
    }

    public record ReviewQueueResponse(
            List<ReviewQueueItemResponse> reviews,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    public record ReviewQueueItemResponse(
            Long id,
            Long activityId,
            String activityNumber,
            String activityName,
            Long projectId,
            String projectName,
            int cycleNumber,
            ReviewKind kind,
            LocalDate weekStartDate,
            Instant submittedAt,
            BigDecimal expectedCheckingHours,
            boolean hasDocument,
            long annotationCount
    ) {
    }

    public record WorkloadResponse(
            long openCount,
            BigDecimal knownExpectedHours,
            long missingEstimateCount,
            BigDecimal availableWeeklyHours,
            BigDecimal remainingHours,
            Boolean queueExceedsWeek,
            boolean completeForecast,
            Instant availabilityUpdatedAt
    ) {
    }

    public record AvailabilityResponse(
            BigDecimal weeklyHours,
            Instant updatedAt
    ) {
    }

    public record UpdateAvailabilityRequest(
            @NotNull(message = "Weekly available hours are required")
            @DecimalMin(value = "0.50", message = "Weekly available hours must be at least 0.50")
            @DecimalMax(value = "168.00", message = "Weekly available hours must not exceed 168")
            @Digits(integer = 3, fraction = 2, message = "Weekly available hours may have at most 2 decimal places")
            BigDecimal weeklyHours
    ) {
    }
}

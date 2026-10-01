package com.easylang.activitymonitoring.dto;

import com.easylang.activitymonitoring.model.ActivityStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class TranslatorActivityDtos {
    private TranslatorActivityDtos() {
    }

    public record ActivityListResponse(List<ActivitySummaryResponse> activities) {
    }

    public record ActivitySummaryResponse(
            Long id,
            String activityNumber,
            String activityName,
            String projectName,
            ActivityStatus status,
            boolean responsible,
            LocalDate assignedDate,
            ProgressResponse progress
    ) {
    }

    public record ActivityDetailResponse(
            Long id,
            String activityNumber,
            String activityName,
            String projectName,
            ActivityStatus status,
            boolean responsible,
            LocalDate assignedDate,
            LocalDate createdDate,
            ProgressResponse progress,
            List<WorkRecordResponse> workRecords
    ) {
    }

    public record ProgressResponse(
            BigDecimal totalTranslatedVolume,
            long recordCount,
            LocalDate lastRecordDate
    ) {
    }

    public record WorkRecordResponse(
            Long id,
            LocalDate recordDate,
            BigDecimal translatedVolume,
            BigDecimal workHours
    ) {
    }

    public record CreateWorkRecordRequest(
            @NotNull(message = "Record date is required")
            LocalDate recordDate,

            @NotNull(message = "Translated volume is required")
            @DecimalMin(value = "0.01", message = "Translated volume must be greater than 0")
            @DecimalMax(value = "1000.00", message = "Translated volume must be 1,000.00 or less")
            @Digits(integer = 4, fraction = 2, message = "Translated volume can have up to 2 decimal places")
            BigDecimal translatedVolume,

            @DecimalMin(value = "0.00", message = "Work hours cannot be negative")
            @DecimalMax(value = "24.00", message = "Work hours must be 24.00 or less")
            @Digits(integer = 2, fraction = 2, message = "Work hours can have up to 2 decimal places")
            BigDecimal workHours
    ) {
    }

    public record UpdateWorkRecordRequest(
            @NotNull(message = "Translated volume is required")
            @DecimalMin(value = "0.01", message = "Translated volume must be greater than 0")
            @DecimalMax(value = "1000.00", message = "Translated volume must be 1,000.00 or less")
            @Digits(integer = 4, fraction = 2, message = "Translated volume can have up to 2 decimal places")
            BigDecimal translatedVolume,

            @DecimalMin(value = "0.00", message = "Work hours cannot be negative")
            @DecimalMax(value = "24.00", message = "Work hours must be 24.00 or less")
            @Digits(integer = 2, fraction = 2, message = "Work hours can have up to 2 decimal places")
            BigDecimal workHours
    ) {
    }
}

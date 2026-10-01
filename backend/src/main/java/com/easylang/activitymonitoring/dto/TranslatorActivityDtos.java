package com.easylang.activitymonitoring.dto;

import com.easylang.activitymonitoring.model.ActivityStatus;
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
            BigDecimal totalTranslatedPages,
            long recordCount,
            LocalDate lastRecordDate
    ) {
    }

    public record WorkRecordResponse(
            Long id,
            LocalDate recordDate,
            BigDecimal translatedVolumePages,
            BigDecimal workHours
    ) {
    }
}

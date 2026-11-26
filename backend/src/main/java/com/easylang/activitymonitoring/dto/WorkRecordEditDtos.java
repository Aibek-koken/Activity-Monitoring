package com.easylang.activitymonitoring.dto;

import com.easylang.activitymonitoring.model.WorkRecordEditRequest.Status;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class WorkRecordEditDtos {
    private WorkRecordEditDtos() { }

    public record CreateRequest(@NotBlank @Size(max = 1000) String reason) { }
    public record DecideRequest(@NotNull Boolean approved, @Size(max = 1000) String comment) { }
    public record Response(Long id, Long activityId, String activityNumber, String projectName,
                           Long workRecordId, LocalDate recordDate, String translatorName,
                           String reason, Status status, String decisionComment,
                           Instant requestedAt, Instant decidedAt) { }
    public record ListResponse(List<Response> requests) { }
}

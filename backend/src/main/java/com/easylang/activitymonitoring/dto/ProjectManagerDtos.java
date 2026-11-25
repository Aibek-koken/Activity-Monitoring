package com.easylang.activitymonitoring.dto;

import com.easylang.activitymonitoring.model.ActivityStatus;
import java.time.LocalDate;
import java.util.List;

public final class ProjectManagerDtos {
    private ProjectManagerDtos() {}

    public record DashboardResponse(List<TranslatorResponse> translators, List<ActivityAssignmentResponse> assignments) {}
    public record TranslatorResponse(Long id, String name, String initials, boolean available, int openActivities) {}
    public record ActivityAssignmentResponse(Long activityId, String activityNumber, String activityName,
            String projectName, ActivityStatus status, List<AssignedTranslatorResponse> translators) {}
    public record AssignedTranslatorResponse(Long translatorId, String translatorName,
            boolean responsible, LocalDate assignedDate) {}
    public record ReplaceTranslatorRequest(Long translatorId) {}
}

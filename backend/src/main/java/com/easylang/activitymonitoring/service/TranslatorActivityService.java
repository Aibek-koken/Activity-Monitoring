package com.easylang.activitymonitoring.service;

import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.ActivityDetailResponse;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.ActivityListResponse;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.ActivitySummaryResponse;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.ProgressResponse;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.WorkRecordResponse;
import com.easylang.activitymonitoring.model.Activity;
import com.easylang.activitymonitoring.model.ActivityTranslator;
import com.easylang.activitymonitoring.model.WorkRecord;
import com.easylang.activitymonitoring.repository.ActivityRepository;
import com.easylang.activitymonitoring.repository.ActivityTranslatorRepository;
import com.easylang.activitymonitoring.repository.WorkRecordRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TranslatorActivityService {

    private final ActivityRepository activityRepository;
    private final ActivityTranslatorRepository activityTranslatorRepository;
    private final WorkRecordRepository workRecordRepository;

    public TranslatorActivityService(
            ActivityRepository activityRepository,
            ActivityTranslatorRepository activityTranslatorRepository,
            WorkRecordRepository workRecordRepository
    ) {
        this.activityRepository = activityRepository;
        this.activityTranslatorRepository = activityTranslatorRepository;
        this.workRecordRepository = workRecordRepository;
    }

    @Transactional(readOnly = true)
    public ActivityListResponse listAssignedActivities(Long translatorId, String query) {
        String normalizedQuery = normalizeQuery(query);
        List<ActivitySummaryResponse> activities = activityRepository
                .findAssignedToTranslator(translatorId, normalizedQuery)
                .stream()
                .map(activity -> toSummary(activity, translatorId))
                .toList();
        return new ActivityListResponse(activities);
    }

    @Transactional(readOnly = true)
    public ActivityDetailResponse getAssignedActivity(Long translatorId, Long activityId) {
        Activity activity = activityRepository.findAssignedToTranslatorById(translatorId, activityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activity not found"));
        ActivityTranslator assignment = assignment(activity.getId(), translatorId);
        List<WorkRecordResponse> workRecords = workRecordRepository
                .findByActivityIdAndTranslatorIdOrderByRecordDateDescIdDesc(activity.getId(), translatorId)
                .stream()
                .map(this::toWorkRecord)
                .toList();

        return new ActivityDetailResponse(
                activity.getId(),
                activity.getActivityNumber(),
                activity.getActivityName(),
                activity.getProject().getProjectName(),
                activity.getStatus(),
                assignment.isResponsible(),
                assignment.getAssignedDate(),
                activity.getCreatedDate(),
                progress(activity.getId(), translatorId),
                workRecords
        );
    }

    private ActivitySummaryResponse toSummary(Activity activity, Long translatorId) {
        ActivityTranslator assignment = assignment(activity.getId(), translatorId);
        return new ActivitySummaryResponse(
                activity.getId(),
                activity.getActivityNumber(),
                activity.getActivityName(),
                activity.getProject().getProjectName(),
                activity.getStatus(),
                assignment.isResponsible(),
                assignment.getAssignedDate(),
                progress(activity.getId(), translatorId)
        );
    }

    private ProgressResponse progress(Long activityId, Long translatorId) {
        BigDecimal total = workRecordRepository.sumTranslatedVolumePages(activityId, translatorId);
        long recordCount = workRecordRepository.countByActivityIdAndTranslatorId(activityId, translatorId);
        return new ProgressResponse(
                normalizeDecimal(total),
                recordCount,
                workRecordRepository.findTopByActivityIdAndTranslatorIdOrderByRecordDateDescIdDesc(activityId, translatorId)
                        .map(WorkRecord::getRecordDate)
                        .orElse(null)
        );
    }

    private WorkRecordResponse toWorkRecord(WorkRecord record) {
        return new WorkRecordResponse(
                record.getId(),
                record.getRecordDate(),
                normalizeDecimal(record.getTranslatedVolumePages()),
                normalizeDecimal(record.getWorkHours())
        );
    }

    private ActivityTranslator assignment(Long activityId, Long translatorId) {
        return activityTranslatorRepository.findByActivityIdAndTranslatorId(activityId, translatorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activity not found"));
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return query.trim().toLowerCase();
    }

    private BigDecimal normalizeDecimal(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}

package com.easylang.activitymonitoring.service;

import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.ActivityDetailResponse;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.ActivityListResponse;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.ActivitySummaryResponse;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.CreateWorkRecordRequest;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.ProgressResponse;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.UpdateWorkRecordRequest;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.WorkRecordResponse;
import com.easylang.activitymonitoring.model.Activity;
import com.easylang.activitymonitoring.model.ActivityTranslator;
import com.easylang.activitymonitoring.model.WorkRecord;
import com.easylang.activitymonitoring.repository.ActivityRepository;
import com.easylang.activitymonitoring.repository.ActivityTranslatorRepository;
import com.easylang.activitymonitoring.repository.WorkRecordRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
        String queryPattern = searchPattern(query);
        List<Activity> assignedActivities = queryPattern == null
                ? activityRepository.findAssignedToTranslator(translatorId)
                : activityRepository.findAssignedToTranslatorMatching(translatorId, queryPattern);
        List<ActivitySummaryResponse> activities = assignedActivities
                .stream()
                .map(activity -> toSummary(activity, translatorId))
                .toList();
        return new ActivityListResponse(activities);
    }

    @Transactional(readOnly = true)
    public ActivityDetailResponse getAssignedActivity(Long translatorId, Long activityId) {
        Activity activity = activityRepository.findAssignedToTranslatorById(translatorId, activityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activity not found"));
        return toDetail(activity, translatorId);
    }

    @Transactional
    public ActivityDetailResponse createWorkRecord(
            Long translatorId,
            Long activityId,
            CreateWorkRecordRequest request
    ) {
        Activity activity = assignedActivity(translatorId, activityId);
        rejectFutureDate(request.recordDate());

        workRecordRepository.findByActivityIdAndTranslatorIdAndRecordDate(
                activity.getId(),
                translatorId,
                request.recordDate()
        ).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A work record already exists for this date");
        });

        workRecordRepository.save(new WorkRecord(
                activity,
                assignment(activity.getId(), translatorId).getTranslator(),
                request.recordDate(),
                normalizeDecimal(request.translatedVolume()),
                normalizeDecimal(request.workHours())
        ));
        activity.markInProgress();

        return toDetail(activity, translatorId);
    }

    @Transactional
    public ActivityDetailResponse updateWorkRecord(
            Long translatorId,
            Long activityId,
            Long recordId,
            UpdateWorkRecordRequest request
    ) {
        Activity activity = assignedActivity(translatorId, activityId);
        WorkRecord record = workRecordRepository.findByIdAndActivityIdAndTranslatorId(recordId, activityId, translatorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work record not found"));

        record.updateWork(normalizeDecimal(request.translatedVolume()), normalizeDecimal(request.workHours()));
        activity.markInProgress();

        return toDetail(activity, translatorId);
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

    private ActivityDetailResponse toDetail(Activity activity, Long translatorId) {
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

    private Activity assignedActivity(Long translatorId, Long activityId) {
        return activityRepository.findAssignedToTranslatorById(translatorId, activityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activity not found"));
    }

    private ProgressResponse progress(Long activityId, Long translatorId) {
        BigDecimal total = workRecordRepository.sumTranslatedVolume(activityId, translatorId);
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
                normalizeDecimal(record.getTranslatedVolume()),
                normalizeDecimal(record.getWorkHours())
        );
    }

    private void rejectFutureDate(LocalDate recordDate) {
        LocalDate latestAllowedDate = LocalDate.now(ZoneOffset.ofHours(14));
        if (recordDate.isAfter(latestAllowedDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Record date cannot be in the future");
        }
    }

    private ActivityTranslator assignment(Long activityId, Long translatorId) {
        return activityTranslatorRepository.findByActivityIdAndTranslatorId(activityId, translatorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activity not found"));
    }

    private String searchPattern(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return "%" + escapeLike(query.trim().toLowerCase()) + "%";
    }

    private String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private BigDecimal normalizeDecimal(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}

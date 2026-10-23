package com.easylang.activitymonitoring.service;

import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.ActivityDetailResponse;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.ActivityPermissionsResponse;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.ActivityListResponse;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.ActivitySummaryResponse;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.CurrentWeekResponse;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.CreateWorkRecordRequest;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.ProgressResponse;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.UpdateWorkRecordRequest;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.WorkRecordResponse;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.WeeklyEstimateResponse;
import com.easylang.activitymonitoring.model.Activity;
import com.easylang.activitymonitoring.model.ActivityTranslator;
import com.easylang.activitymonitoring.model.ActivityStatus;
import com.easylang.activitymonitoring.model.WorkRecord;
import com.easylang.activitymonitoring.model.WeeklyEstimate;
import com.easylang.activitymonitoring.repository.ActivityRepository;
import com.easylang.activitymonitoring.repository.ActivityTranslatorRepository;
import com.easylang.activitymonitoring.repository.WorkRecordRepository;
import com.easylang.activitymonitoring.repository.WeeklyEstimateRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
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
    private final WeeklyEstimateRepository weeklyEstimateRepository;
    private final Clock businessClock;

    public TranslatorActivityService(
            ActivityRepository activityRepository,
            ActivityTranslatorRepository activityTranslatorRepository,
            WorkRecordRepository workRecordRepository,
            WeeklyEstimateRepository weeklyEstimateRepository,
            Clock businessClock
    ) {
        this.activityRepository = activityRepository;
        this.activityTranslatorRepository = activityTranslatorRepository;
        this.workRecordRepository = workRecordRepository;
        this.weeklyEstimateRepository = weeklyEstimateRepository;
        this.businessClock = businessClock;
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
        requireEditable(activity);
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
        requireEditable(activity);
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
        LocalDate weekStart = LocalDate.now(businessClock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<WeeklyEstimateResponse> weeklyEstimates = weeklyEstimateRepository
                .findByActivityIdOrderByWeekStartDateDesc(activity.getId())
                .stream()
                .map(this::toWeeklyEstimate)
                .toList();
        WeeklyEstimateResponse currentEstimate = weeklyEstimates.stream()
                .filter(estimate -> estimate.weekStartDate().equals(weekStart))
                .findFirst()
                .orElse(null);
        boolean active = activity.getStatus() == ActivityStatus.ASSIGNED
                || activity.getStatus() == ActivityStatus.IN_PROGRESS;
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
                workRecords,
                new CurrentWeekResponse(weekStart, weekStart.plusDays(6)),
                currentEstimate,
                weeklyEstimates,
                new ActivityPermissionsResponse(
                        active,
                        active && assignment.isResponsible(),
                        active && assignment.isResponsible() && currentEstimate != null
                                && currentEstimate.remainingTextVolume().signum() == 0,
                        activity.getStatus() == ActivityStatus.FINISHED && assignment.isResponsible()
                )
        );
    }

    private WeeklyEstimateResponse toWeeklyEstimate(WeeklyEstimate estimate) {
        return new WeeklyEstimateResponse(
                estimate.getId(),
                estimate.getWeekStartDate(),
                estimate.getRemainingTextVolume(),
                estimate.getUpdatedAt()
        );
    }

    private void requireEditable(Activity activity) {
        if (activity.getStatus() != ActivityStatus.ASSIGNED && activity.getStatus() != ActivityStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Work records are locked for this activity");
        }
    }

    private Activity assignedActivity(Long translatorId, Long activityId) {
        Activity activity = activityRepository.findByIdForUpdate(activityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activity not found"));
        assignment(activityId, translatorId);
        return activity;
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

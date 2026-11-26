package com.easylang.activitymonitoring.service;

import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.ActivityDetailResponse;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.ActivityPermissionsResponse;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.ActivityListResponse;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.ActivitySummaryResponse;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.CurrentWeekResponse;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.CreateWorkRecordRequest;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.ProgressResponse;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.ReworkSummaryResponse;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.UpdateWorkRecordRequest;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.WorkRecordResponse;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.WeeklyEstimateResponse;
import com.easylang.activitymonitoring.model.Activity;
import com.easylang.activitymonitoring.model.ActivityTranslator;
import com.easylang.activitymonitoring.model.ActivityStatus;
import com.easylang.activitymonitoring.model.Review;
import com.easylang.activitymonitoring.model.ReviewResult;
import com.easylang.activitymonitoring.model.WorkRecord;
import com.easylang.activitymonitoring.model.WeeklyEstimate;
import com.easylang.activitymonitoring.repository.ActivityRepository;
import com.easylang.activitymonitoring.repository.ActivityTranslatorRepository;
import com.easylang.activitymonitoring.repository.ProjectChiefEditorRepository;
import com.easylang.activitymonitoring.repository.ReviewRepository;
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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private final ProjectChiefEditorRepository projectChiefEditorRepository;
    private final ReviewRepository reviewRepository;
    private final WorkRecordEditService workRecordEditService;
    private final Clock businessClock;

    public TranslatorActivityService(
            ActivityRepository activityRepository,
            ActivityTranslatorRepository activityTranslatorRepository,
            WorkRecordRepository workRecordRepository,
            WeeklyEstimateRepository weeklyEstimateRepository,
            ProjectChiefEditorRepository projectChiefEditorRepository,
            ReviewRepository reviewRepository,
            WorkRecordEditService workRecordEditService,
            Clock businessClock
    ) {
        this.activityRepository = activityRepository;
        this.activityTranslatorRepository = activityTranslatorRepository;
        this.workRecordRepository = workRecordRepository;
        this.weeklyEstimateRepository = weeklyEstimateRepository;
        this.projectChiefEditorRepository = projectChiefEditorRepository;
        this.reviewRepository = reviewRepository;
        this.workRecordEditService = workRecordEditService;
        this.businessClock = businessClock;
    }

    @Transactional(readOnly = true)
    public ActivityListResponse listAssignedActivities(Long translatorId, String query) {
        String queryPattern = searchPattern(query);
        List<Activity> assignedActivities = queryPattern == null
                ? activityRepository.findAssignedToTranslator(translatorId)
                : activityRepository.findAssignedToTranslatorMatching(translatorId, queryPattern);
        if (assignedActivities.isEmpty()) return new ActivityListResponse(List.of());
        List<Long> ids = assignedActivities.stream().map(Activity::getId).toList();
        Map<Long, ActivityTranslator> assignments = activityTranslatorRepository
                .findForActivityList(translatorId, ids).stream()
                .collect(Collectors.toMap(assignment -> assignment.getActivity().getId(), Function.identity()));
        Map<Long, WorkRecordRepository.ActivityProgressRow> progressRows = workRecordRepository
                .findProgressForActivityList(translatorId, ids).stream()
                .collect(Collectors.toMap(WorkRecordRepository.ActivityProgressRow::getActivityId, Function.identity()));
        List<ActivitySummaryResponse> activities = assignedActivities
                .stream()
                .map(activity -> toSummary(activity, assignments.get(activity.getId()), progressRows.get(activity.getId())))
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
        ReworkState rework = reworkState(activityId, translatorId);
        ReworkAllocation allocation = allocateRework(activity, request.translatedVolume(), request.reworkPages(), rework, null);

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
                normalizeDecimal(request.workHours()),
                allocation.reworkPages(),
                allocation.reworkReview()
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
        workRecordEditService.useApproval(translatorId, activityId, recordId, request.editRequestId());
        ReworkState rework = reworkState(activityId, translatorId);
        ReworkAllocation allocation = allocateRework(activity, request.translatedVolume(), request.reworkPages(), rework, record);

        record.updateWork(
                normalizeDecimal(request.translatedVolume()),
                normalizeDecimal(request.workHours()),
                allocation.reworkPages(),
                allocation.reworkReview()
        );
        activity.markInProgress();

        return toDetail(activity, translatorId);
    }

    private ActivitySummaryResponse toSummary(Activity activity, ActivityTranslator assignment,
                                              WorkRecordRepository.ActivityProgressRow progressRow) {
        ProgressResponse progress = progressRow == null
                ? new ProgressResponse(BigDecimal.ZERO.setScale(2), 0, null)
                : new ProgressResponse(normalizeDecimal(progressRow.getTotal()),
                        progressRow.getRecordCount(), progressRow.getLastRecordDate());
        return new ActivitySummaryResponse(
                activity.getId(),
                activity.getActivityNumber(),
                activity.getActivityName(),
                activity.getProject().getProjectName(),
                activity.getStatus(),
                assignment.isResponsible(),
                assignment.getAssignedDate(),
                progress
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
        boolean hasResponsibleChiefEditor = projectChiefEditorRepository
                .findByProjectIdAndResponsibleTrue(activity.getProject().getId())
                .isPresent();
        Review latestReview = reviewRepository.findTopByActivityIdOrderByCycleNumberDesc(activity.getId()).orElse(null);
        boolean openReview = latestReview != null && latestReview.isOpen();
        ReworkState reworkState = reworkState(activity.getId(), translatorId);
        BigDecimal outstandingRework = reworkState.outstanding();
        boolean hasOutstandingRework = outstandingRework.signum() > 0;
        BigDecimal remainingPages = currentEstimate == null
                ? null
                : normalizeDecimal(currentEstimate.remainingTextVolume());
        BigDecimal totalRemaining = (remainingPages == null ? BigDecimal.ZERO : remainingPages).add(outstandingRework);
        ReworkSummaryResponse rework = new ReworkSummaryResponse(
                reworkState.review() == null ? null : Math.toIntExact(reworkState.review().getId()),
                reworkState.review() == null ? null : reworkState.review().getCorrectionPages(),
                normalizeDecimal(reworkState.recorded()),
                normalizeDecimal(outstandingRework),
                remainingPages,
                normalizeDecimal(totalRemaining),
                currentEstimate != null
        );
        boolean canEditWorkRecords = active || (activity.getStatus() == ActivityStatus.CORRECTION_REQUIRED
                && hasOutstandingRework);
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
                rework,
                new ActivityPermissionsResponse(
                        canEditWorkRecords,
                        active && assignment.isResponsible(),
                        active && assignment.isResponsible() && currentEstimate != null
                                && currentEstimate.remainingTextVolume().signum() == 0
                                && !openReview && !hasOutstandingRework,
                        activity.getStatus() == ActivityStatus.FINISHED && assignment.isResponsible()
                                && hasResponsibleChiefEditor && !openReview,
                        active && assignment.isResponsible() && !openReview && hasResponsibleChiefEditor
                                && (latestReview == null || latestReview.getResult() != ReviewResult.CORRECTION_REQUESTED),
                        hasResponsibleChiefEditor,
                        openReview,
                        hasOutstandingRework
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
        boolean active = activity.getStatus() == ActivityStatus.ASSIGNED
                || activity.getStatus() == ActivityStatus.IN_PROGRESS;
        if (!active && activity.getStatus() != ActivityStatus.CORRECTION_REQUIRED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Work records are locked for this activity");
        }
    }

    private ReworkAllocation allocateRework(
            Activity activity,
            BigDecimal translatedVolume,
            BigDecimal requestedReworkPages,
            ReworkState rework,
            WorkRecord existingRecord
    ) {
        BigDecimal reworkPages = normalizeDecimal(requestedReworkPages == null ? BigDecimal.ZERO : requestedReworkPages);
        BigDecimal volume = normalizeDecimal(translatedVolume == null ? BigDecimal.ZERO : translatedVolume);
        if (volume.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Translated pages cannot be negative");
        }
        if (reworkPages.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rework pages cannot be negative");
        }
        if (activity.getStatus() == ActivityStatus.CORRECTION_REQUIRED && volume.signum() > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only rework pages and working time can be recorded while corrections are requested"
            );
        }
        if (reworkPages.signum() > 0) {
            if (rework.review() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "There is no outstanding correction to account for");
            }
            BigDecimal alreadyAllocated = rework.recorded();
            if (existingRecord != null && existingRecord.getReworkReview() != null
                    && existingRecord.getReworkReview().getId().equals(rework.review().getId())) {
                alreadyAllocated = alreadyAllocated.subtract(existingRecord.getReworkPages());
            }
            BigDecimal allowed = rework.correctionPages().subtract(alreadyAllocated);
            if (reworkPages.compareTo(allowed) > 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Rework pages exceed the outstanding correction pages"
                );
            }
            return new ReworkAllocation(reworkPages, rework.review());
        }
        if (volume.signum() == 0 && (existingRecord == null || existingRecord.getTranslatedVolume().signum() == 0)) {
            if (activity.getStatus() != ActivityStatus.CORRECTION_REQUIRED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Record translated pages or rework pages");
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Record at least one rework page");
        }
        return new ReworkAllocation(BigDecimal.ZERO, null);
    }

    private ReworkState reworkState(Long activityId, Long translatorId) {
        Review latest = reviewRepository.findTopByActivityIdOrderByCycleNumberDesc(activityId).orElse(null);
        if (latest == null
                || latest.getResult() != ReviewResult.CORRECTION_REQUESTED
                || latest.getCorrectionPages() == null) {
            return new ReworkState(null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal correctionPages = BigDecimal.valueOf(latest.getCorrectionPages());
        BigDecimal recorded = normalizeDecimal(
                workRecordRepository.sumReworkPagesForReview(activityId, translatorId, latest.getId())
        );
        BigDecimal outstanding = correctionPages.subtract(recorded).max(BigDecimal.ZERO);
        return new ReworkState(latest, correctionPages, recorded, normalizeDecimal(outstanding));
    }

    private record ReworkState(
            Review review,
            BigDecimal correctionPages,
            BigDecimal recorded,
            BigDecimal outstanding
    ) {
    }

    private record ReworkAllocation(BigDecimal reworkPages, Review reworkReview) {
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
                normalizeDecimal(record.getReworkPages()),
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

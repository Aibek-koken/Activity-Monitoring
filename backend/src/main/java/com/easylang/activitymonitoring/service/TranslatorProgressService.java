package com.easylang.activitymonitoring.service;

import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.ActivityDetailResponse;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.SaveWeeklyEstimateRequest;
import com.easylang.activitymonitoring.model.Activity;
import com.easylang.activitymonitoring.model.ActivityStatus;
import com.easylang.activitymonitoring.model.ActivityTranslator;
import com.easylang.activitymonitoring.model.Review;
import com.easylang.activitymonitoring.model.Role;
import com.easylang.activitymonitoring.model.WeeklyEstimate;
import com.easylang.activitymonitoring.repository.ActivityRepository;
import com.easylang.activitymonitoring.repository.ActivityTranslatorRepository;
import com.easylang.activitymonitoring.repository.ProjectChiefEditorRepository;
import com.easylang.activitymonitoring.repository.ReviewRepository;
import com.easylang.activitymonitoring.repository.WeeklyEstimateRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TranslatorProgressService {

    private final ActivityRepository activityRepository;
    private final ActivityTranslatorRepository assignmentRepository;
    private final WeeklyEstimateRepository estimateRepository;
    private final ProjectChiefEditorRepository editorRepository;
    private final ReviewRepository reviewRepository;
    private final TranslatorActivityService activityService;
    private final Clock businessClock;

    public TranslatorProgressService(ActivityRepository activityRepository,
                                     ActivityTranslatorRepository assignmentRepository,
                                     WeeklyEstimateRepository estimateRepository,
                                     ProjectChiefEditorRepository editorRepository,
                                     ReviewRepository reviewRepository,
                                     TranslatorActivityService activityService,
                                     Clock businessClock) {
        this.activityRepository = activityRepository;
        this.assignmentRepository = assignmentRepository;
        this.estimateRepository = estimateRepository;
        this.editorRepository = editorRepository;
        this.reviewRepository = reviewRepository;
        this.activityService = activityService;
        this.businessClock = businessClock;
    }

    @Transactional
    public ActivityDetailResponse saveCurrentEstimate(Long translatorId, Long activityId,
                                                       SaveWeeklyEstimateRequest request) {
        Activity activity = lockedActivity(activityId);
        ActivityTranslator assignment = responsibleAssignment(activityId, translatorId);
        if (activity.getStatus() != ActivityStatus.ASSIGNED && activity.getStatus() != ActivityStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Weekly estimates are locked for this activity");
        }

        LocalDate weekStart = LocalDate.now(businessClock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        WeeklyEstimate estimate = estimateRepository.findByActivityIdAndWeekStartDate(activityId, weekStart)
                .orElseGet(() -> new WeeklyEstimate(
                        activity, assignment.getTranslator(), weekStart, request.remainingTextVolume(), businessClock.instant()
                ));
        estimate.updateRemainingTextVolume(request.remainingTextVolume(), businessClock.instant());
        estimateRepository.save(estimate);
        return activityService.getAssignedActivity(translatorId, activityId);
    }

    @Transactional
    public ActivityDetailResponse finish(Long translatorId, Long activityId) {
        Activity activity = lockedActivity(activityId);
        responsibleAssignment(activityId, translatorId);
        if (activity.getStatus() != ActivityStatus.ASSIGNED && activity.getStatus() != ActivityStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only an active activity can be finished");
        }

        LocalDate weekStart = LocalDate.now(businessClock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        WeeklyEstimate estimate = estimateRepository.findByActivityIdAndWeekStartDate(activityId, weekStart)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Save zero remaining volume for the current week before finishing"
                ));
        if (estimate.getRemainingTextVolume().signum() != 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Current week's remaining volume must be 0 before finishing"
            );
        }

        activity.finish();
        return activityService.getAssignedActivity(translatorId, activityId);
    }

    @Transactional
    public ActivityDetailResponse submitForChecking(Long translatorId, Long activityId) {
        Activity activity = lockedActivity(activityId);
        responsibleAssignment(activityId, translatorId);
        if (activity.getStatus() != ActivityStatus.FINISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a finished activity can be submitted for checking");
        }

        var editorAssignment = editorRepository.findByProjectIdAndResponsibleTrue(activity.getProject().getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "No responsible Chief Editor is assigned to this project"
                ));
        var editor = editorAssignment.getChiefEditor();
        if (!editor.isActive() || editor.getRole() != Role.CHIEF_EDITOR) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The responsible Chief Editor is unavailable");
        }
        if (reviewRepository.existsByActivityIdAndCycleNumber(activityId, 1)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This activity has already been submitted");
        }

        reviewRepository.save(new Review(activity, editor, 1, businessClock.instant()));
        activity.submitForChecking();
        return activityService.getAssignedActivity(translatorId, activityId);
    }

    private Activity lockedActivity(Long activityId) {
        return activityRepository.findByIdForUpdate(activityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activity not found"));
    }

    private ActivityTranslator responsibleAssignment(Long activityId, Long translatorId) {
        ActivityTranslator assignment = assignmentRepository.findByActivityIdAndTranslatorId(activityId, translatorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activity not found"));
        if (!assignment.isResponsible()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the responsible translator can perform this action");
        }
        return assignment;
    }
}

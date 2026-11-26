package com.easylang.activitymonitoring.service;

import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.ActivityDetailResponse;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.SaveWeeklyEstimateRequest;
import com.easylang.activitymonitoring.model.Activity;
import com.easylang.activitymonitoring.model.ActivityStatus;
import com.easylang.activitymonitoring.model.ActivityTranslator;
import com.easylang.activitymonitoring.model.Review;
import com.easylang.activitymonitoring.model.ReviewKind;
import com.easylang.activitymonitoring.model.ReviewResult;
import com.easylang.activitymonitoring.model.Role;
import com.easylang.activitymonitoring.model.WeeklyEstimate;
import com.easylang.activitymonitoring.repository.ActivityRepository;
import com.easylang.activitymonitoring.repository.ActivityTranslatorRepository;
import com.easylang.activitymonitoring.repository.ProjectChiefEditorRepository;
import com.easylang.activitymonitoring.repository.ReviewRepository;
import com.easylang.activitymonitoring.repository.WeeklyEstimateRepository;
import com.easylang.activitymonitoring.storage.DocumentUploadService;
import com.easylang.activitymonitoring.storage.UploadedDocument;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TranslatorProgressService {

    private final ActivityRepository activityRepository;
    private final ActivityTranslatorRepository assignmentRepository;
    private final WeeklyEstimateRepository estimateRepository;
    private final ProjectChiefEditorRepository editorRepository;
    private final ReviewRepository reviewRepository;
    private final TranslatorActivityService activityService;
    private final DocumentUploadService documentUploadService;
    private final Clock businessClock;

    public TranslatorProgressService(ActivityRepository activityRepository,
                                     ActivityTranslatorRepository assignmentRepository,
                                     WeeklyEstimateRepository estimateRepository,
                                     ProjectChiefEditorRepository editorRepository,
                                     ReviewRepository reviewRepository,
                                     TranslatorActivityService activityService,
                                     DocumentUploadService documentUploadService,
                                     Clock businessClock) {
        this.activityRepository = activityRepository;
        this.assignmentRepository = assignmentRepository;
        this.estimateRepository = estimateRepository;
        this.editorRepository = editorRepository;
        this.reviewRepository = reviewRepository;
        this.activityService = activityService;
        this.documentUploadService = documentUploadService;
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

        LocalDate weekStart = currentWeekStart();
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
        if (reviewRepository.findFirstByActivityIdAndResultIsNullOrderByCycleNumberDesc(activityId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Resolve the open review before finishing");
        }
        Review latest = reviewRepository.findTopByActivityIdOrderByCycleNumberDesc(activityId).orElse(null);
        if (latest != null && latest.getResult() == ReviewResult.CORRECTION_REQUESTED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Complete the requested correction before finishing"
            );
        }

        LocalDate weekStart = currentWeekStart();
        WeeklyEstimate estimate = estimateRepository.findByActivityIdAndWeekStartDate(activityId, weekStart)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Save zero remaining pages for the current week before finishing"
                ));
        if (estimate.getRemainingTextVolume().signum() != 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Current week's remaining pages must be 0 before finishing"
            );
        }

        activity.finish();
        return activityService.getAssignedActivity(translatorId, activityId);
    }

    @Transactional
    public ActivityDetailResponse submitForChecking(Long translatorId, Long activityId, MultipartFile file) {
        Activity activity = lockedActivity(activityId);
        responsibleAssignment(activityId, translatorId);
        if (activity.getStatus() != ActivityStatus.FINISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a finished activity can be submitted for checking");
        }
        requireNoOpenReview(activityId);
        var editor = responsibleEditor(activity);
        int nextCycle = nextCycle(activityId);
        UploadedDocument document = documentUploadService.validateAndStore(file);
        if (reviewRepository.existsByActivityIdAndCycleNumber(activityId, nextCycle)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This activity has already been submitted");
        }
        Review review = new Review(activity, editor, nextCycle, ReviewKind.FINAL, null, businessClock.instant());
        review.attachDocument(
                document.storageKey(), document.originalName(), document.contentType(), document.sizeBytes(), document.sha256()
        );
        reviewRepository.save(review);
        activity.submitForChecking();
        return activityService.getAssignedActivity(translatorId, activityId);
    }

    @Transactional
    public ActivityDetailResponse submitWeeklyVersion(Long translatorId, Long activityId, MultipartFile file) {
        Activity activity = lockedActivity(activityId);
        responsibleAssignment(activityId, translatorId);
        if (activity.getStatus() != ActivityStatus.ASSIGNED && activity.getStatus() != ActivityStatus.IN_PROGRESS) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Weekly versions can only be sent while the activity is active"
            );
        }
        Review latest = reviewRepository.findTopByActivityIdOrderByCycleNumberDesc(activityId).orElse(null);
        if (latest != null && latest.getResult() == ReviewResult.CORRECTION_REQUESTED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Send the requested correction through the correction upload"
            );
        }

        LocalDate weekStart = currentWeekStart();
        UploadedDocument document = documentUploadService.validateAndStore(file);

        List<Review> weeklyReviews = reviewRepository.findByActivityIdAndKindAndWeekStartDateOrderByCycleNumberDesc(
                activityId, ReviewKind.WEEKLY, weekStart
        );
        boolean duplicateDocument = weeklyReviews.stream()
                .anyMatch(review -> document.sha256().equals(review.getDocumentSha256()));
        if (duplicateDocument) {
            documentUploadService.discard(document.storageKey());
            return activityService.getAssignedActivity(translatorId, activityId);
        }
        requireNoOpenReview(activityId);

        var editor = responsibleEditor(activity);
        int nextCycle = nextCycle(activityId);
        Review review = new Review(activity, editor, nextCycle, ReviewKind.WEEKLY, weekStart, businessClock.instant());
        review.attachDocument(
                document.storageKey(), document.originalName(), document.contentType(), document.sizeBytes(), document.sha256()
        );
        reviewRepository.saveAndFlush(review);
        return activityService.getAssignedActivity(translatorId, activityId);
    }

    @Transactional
    public ActivityDetailResponse resubmitForChecking(Long translatorId, Long activityId, MultipartFile file) {
        Activity activity = lockedActivity(activityId);
        responsibleAssignment(activityId, translatorId);
        Review previous = reviewRepository.findTopByActivityIdOrderByCycleNumberDesc(activityId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "No prior review exists for this activity"
                ));
        if (previous.getResult() != ReviewResult.CORRECTION_REQUESTED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "The latest review does not request corrections"
            );
        }
        requireNoOpenReview(activityId);
        if (previous.getKind() == ReviewKind.FINAL && activity.getStatus() != ActivityStatus.CORRECTION_REQUIRED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only an activity requiring correction can be resubmitted"
            );
        }
        if (previous.getKind() == ReviewKind.WEEKLY
                && activity.getStatus() != ActivityStatus.ASSIGNED
                && activity.getStatus() != ActivityStatus.IN_PROGRESS) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only an active activity can receive a corrected weekly version"
            );
        }

        var editor = responsibleEditor(activity);
        int nextCycle = previous.getCycleNumber() + 1;
        if (reviewRepository.existsByActivityIdAndCycleNumber(activityId, nextCycle)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This correction has already been resubmitted");
        }

        UploadedDocument document = documentUploadService.validateAndStore(file);
        Review review = new Review(
                activity,
                editor,
                nextCycle,
                previous.getKind(),
                previous.getWeekStartDate(),
                businessClock.instant()
        );
        review.attachDocument(
                document.storageKey(), document.originalName(), document.contentType(), document.sizeBytes(), document.sha256()
        );
        reviewRepository.saveAndFlush(review);
        if (previous.getKind() == ReviewKind.FINAL) {
            activity.resubmitForChecking();
        }
        return activityService.getAssignedActivity(translatorId, activityId);
    }

    private void requireNoOpenReview(Long activityId) {
        if (reviewRepository.findFirstByActivityIdAndResultIsNullOrderByCycleNumberDesc(activityId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A version is already under review");
        }
    }

    private com.easylang.activitymonitoring.model.User responsibleEditor(Activity activity) {
        var editorAssignment = editorRepository.findByProjectIdAndResponsibleTrue(activity.getProject().getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "No responsible Chief Editor is assigned to this project"
                ));
        var editor = editorAssignment.getChiefEditor();
        if (!editor.isActive() || editor.getRole() != Role.CHIEF_EDITOR) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The responsible Chief Editor is unavailable");
        }
        return editor;
    }

    private int nextCycle(Long activityId) {
        return reviewRepository.findTopByActivityIdOrderByCycleNumberDesc(activityId)
                .map(review -> review.getCycleNumber() + 1)
                .orElse(1);
    }

    private LocalDate currentWeekStart() {
        return LocalDate.now(businessClock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
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

package com.easylang.activitymonitoring.service;

import com.easylang.activitymonitoring.dto.ReviewDtos.AnnotationResponse;
import com.easylang.activitymonitoring.dto.ReviewDtos.ChiefEditorReviewDetailResponse;
import com.easylang.activitymonitoring.dto.ReviewDtos.DocumentMetadataResponse;
import com.easylang.activitymonitoring.dto.ReviewDtos.ReviewCycleResponse;
import com.easylang.activitymonitoring.dto.ReviewDtos.ReviewDecisionRequest;
import com.easylang.activitymonitoring.dto.ReviewDtos.UpdateExpectedTimeRequest;
import com.easylang.activitymonitoring.model.Activity;
import com.easylang.activitymonitoring.model.ActivityStatus;
import com.easylang.activitymonitoring.model.ActivityTranslator;
import com.easylang.activitymonitoring.model.Notification;
import com.easylang.activitymonitoring.model.Review;
import com.easylang.activitymonitoring.model.ReviewKind;
import com.easylang.activitymonitoring.model.ReviewResult;
import com.easylang.activitymonitoring.repository.ActivityRepository;
import com.easylang.activitymonitoring.repository.ActivityTranslatorRepository;
import com.easylang.activitymonitoring.repository.ReviewRepository;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChiefEditorReviewService {

    private final ReviewRepository reviewRepository;
    private final ActivityRepository activityRepository;
    private final ActivityTranslatorRepository assignmentRepository;
    private final NotificationWriter notificationWriter;
    private final ReviewAnnotationService annotationService;
    private final Clock businessClock;

    public ChiefEditorReviewService(
            ReviewRepository reviewRepository,
            ActivityRepository activityRepository,
            ActivityTranslatorRepository assignmentRepository,
            NotificationWriter notificationWriter,
            ReviewAnnotationService annotationService,
            Clock businessClock
    ) {
        this.reviewRepository = reviewRepository;
        this.activityRepository = activityRepository;
        this.assignmentRepository = assignmentRepository;
        this.notificationWriter = notificationWriter;
        this.annotationService = annotationService;
        this.businessClock = businessClock;
    }

    @Transactional(readOnly = true)
    public ChiefEditorReviewDetailResponse getReview(Long chiefEditorId, Long reviewId) {
        return toDetail(ownedReview(chiefEditorId, reviewId));
    }

    @Transactional
    public ChiefEditorReviewDetailResponse updateExpectedTime(
            Long chiefEditorId,
            Long reviewId,
            UpdateExpectedTimeRequest request
    ) {
        Review review = lockedOwnedReview(chiefEditorId, reviewId);
        requireOpen(review);
        review.schedule(request.expectedCheckingHours().setScale(2, RoundingMode.UNNECESSARY));
        reviewRepository.saveAndFlush(review);
        return toDetail(review);
    }

    @Transactional
    public ChiefEditorReviewDetailResponse decide(
            Long chiefEditorId,
            Long reviewId,
            ReviewDecisionRequest request
    ) {
        Review review = lockedOwnedReview(chiefEditorId, reviewId);
        requireOpen(review);
        String feedback = normalizeFeedback(request.feedback());
        if (request.correctionPageNumbers() != null && request.correctionPageNumbers().stream()
                .anyMatch(number -> number == null || number < 1 || number > 10000)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid correction page number");
        }
        List<Integer> pageNumbers = request.correctionPageNumbers() == null ? List.of()
                : request.correctionPageNumbers().stream().distinct().sorted().toList();
        Integer correctionCount = pageNumbers.isEmpty() ? request.correctionPages() : Integer.valueOf(pageNumbers.size());
        if (request.result() == ReviewResult.CORRECTION_REQUESTED) {
            if (feedback == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Correction feedback is required");
            }
            if (correctionCount == null || correctionCount <= 0 || correctionCount > 10000) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Correction pages are required");
            }
        }

        var now = businessClock.instant();
        review.close(
                request.result(),
                feedback,
                request.result() == ReviewResult.CORRECTION_REQUESTED ? correctionCount : null,
                now,
                LocalDate.now(businessClock)
        );
        if (request.result() == ReviewResult.CORRECTION_REQUESTED) review.setCorrectionPageNumbers(pageNumbers);
        if (request.result() == ReviewResult.CORRECTION_REQUESTED) {
            ActivityTranslator responsible = assignmentRepository
                    .findByActivityIdAndResponsibleTrue(review.getActivity().getId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT, "No responsible translator is assigned to this activity"
                    ));
            if (review.getKind() == ReviewKind.FINAL) {
                review.getActivity().requestCorrection();
            }
            reviewRepository.saveAndFlush(review);
            notificationWriter.insertInCurrentTransaction(Notification.correctionRequested(
                    responsible.getTranslator(),
                    review.getActivity(),
                    review.getId(),
                    LocalDate.now(businessClock),
                    "Corrections requested for " + review.getActivity().getActivityNumber()
                            + " (cycle " + review.getCycleNumber() + ", "
                            + correctionCount + " pages).",
                    now
            ));
        } else {
            if (review.getKind() == ReviewKind.FINAL) {
                review.getActivity().completeReview();
            }
            reviewRepository.saveAndFlush(review);
        }
        return toDetail(review);
    }

    private Review ownedReview(Long chiefEditorId, Long reviewId) {
        return reviewRepository.findOwnedById(reviewId, chiefEditorId)
                .orElseThrow(() -> notFound());
    }

    private Review lockedOwnedReview(Long chiefEditorId, Long reviewId) {
        Long activityId = reviewRepository.findOwnedActivityId(reviewId, chiefEditorId)
                .orElseThrow(() -> notFound());
        Activity activity = activityRepository.findByIdForUpdate(activityId)
                .orElseThrow(() -> notFound());
        Review locked = reviewRepository.findByIdAndActivityIdForUpdate(reviewId, activity.getId())
                .orElseThrow(() -> notFound());
        if (!locked.getChiefEditor().getId().equals(chiefEditorId)) {
            throw notFound();
        }
        return locked;
    }

    private void requireOpen(Review review) {
        if (!review.isOpen()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This review is already closed");
        }
        if (review.getKind() == ReviewKind.FINAL && review.getActivity().getStatus() != ActivityStatus.UNDER_CHECKING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This review is already closed");
        }
        if (review.getKind() == ReviewKind.WEEKLY
                && review.getActivity().getStatus() != ActivityStatus.ASSIGNED
                && review.getActivity().getStatus() != ActivityStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This weekly version is no longer reviewable");
        }
    }

    private ChiefEditorReviewDetailResponse toDetail(Review review) {
        List<AnnotationResponse> annotations = annotationService.list(review.getId());
        DocumentMetadataResponse document = review.hasDocument()
                ? new DocumentMetadataResponse(
                        review.getDocumentOriginalName(),
                        review.getDocumentContentType(),
                        review.getDocumentSizeBytes(),
                        review.getDocumentSha256(),
                        "/api/v1/reviews/" + review.getId() + "/document",
                        "application/pdf".equals(review.getDocumentContentType())
                                ? "/api/v1/reviews/" + review.getId() + "/annotated-document"
                                : null
                )
                : null;
        Activity activity = review.getActivity();
        List<ReviewCycleResponse> previous = reviewRepository
                .findByActivityIdOrderByCycleNumberDesc(activity.getId())
                .stream()
                .filter(cycle -> !cycle.getId().equals(review.getId()))
                .map(this::toCycle)
                .toList();
        return new ChiefEditorReviewDetailResponse(
                review.getId(),
                activity.getId(),
                activity.getActivityNumber(),
                activity.getActivityName(),
                activity.getProject().getId(),
                activity.getProject().getProjectName(),
                activity.getStatus(),
                review.getCycleNumber(),
                review.getKind(),
                review.getWeekStartDate(),
                review.getSubmittedAt(),
                review.getResult(),
                review.getMistakesNotes(),
                review.getExpectedCheckingHours(),
                review.getCorrectionPages(),
                review.getDecidedAt(),
                document,
                annotations,
                previous,
                review.getCorrectionPageNumbers()
        );
    }

    private ReviewCycleResponse toCycle(Review review) {
        DocumentMetadataResponse document = review.hasDocument()
                ? new DocumentMetadataResponse(
                        review.getDocumentOriginalName(),
                        review.getDocumentContentType(),
                        review.getDocumentSizeBytes(),
                        review.getDocumentSha256(),
                        "/api/v1/reviews/" + review.getId() + "/document",
                        "application/pdf".equals(review.getDocumentContentType())
                                ? "/api/v1/reviews/" + review.getId() + "/annotated-document"
                                : null
                )
                : null;
        long annotationCount = annotationService.count(review.getId());
        return new ReviewCycleResponse(
                review.getId(),
                review.getCycleNumber(),
                review.getKind(),
                review.getWeekStartDate(),
                review.getSubmittedAt(),
                review.getResult(),
                review.getMistakesNotes(),
                review.getExpectedCheckingHours(),
                review.getCorrectionPages(),
                review.getDecidedAt(),
                annotationCount,
                document,
                review.getCorrectionPageNumbers()
        );
    }

    private String normalizeFeedback(String feedback) {
        if (feedback == null || feedback.isBlank()) {
            return null;
        }
        return feedback.trim();
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found");
    }
}

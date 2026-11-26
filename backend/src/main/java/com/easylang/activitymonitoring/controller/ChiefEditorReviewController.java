package com.easylang.activitymonitoring.controller;

import com.easylang.activitymonitoring.dto.ReviewDtos.AnnotationResponse;
import com.easylang.activitymonitoring.dto.ReviewDtos.ChiefEditorReviewDetailResponse;
import com.easylang.activitymonitoring.dto.ReviewDtos.CreateAnnotationRequest;
import com.easylang.activitymonitoring.dto.ReviewDtos.ReviewDecisionRequest;
import com.easylang.activitymonitoring.dto.ReviewDtos.UpdateAnnotationRequest;
import com.easylang.activitymonitoring.dto.ReviewDtos.UpdateExpectedTimeRequest;
import com.easylang.activitymonitoring.security.AuthenticatedUser;
import com.easylang.activitymonitoring.service.ChiefEditorReviewService;
import com.easylang.activitymonitoring.service.ReviewAnnotationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chief-editor/reviews")
public class ChiefEditorReviewController {

    private final ChiefEditorReviewService reviewService;
    private final ReviewAnnotationService annotationService;

    public ChiefEditorReviewController(
            ChiefEditorReviewService reviewService,
            ReviewAnnotationService annotationService
    ) {
        this.reviewService = reviewService;
        this.annotationService = annotationService;
    }

    @GetMapping("/{reviewId}")
    public ChiefEditorReviewDetailResponse getReview(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long reviewId
    ) {
        return reviewService.getReview(principal.user().getId(), reviewId);
    }

    @PutMapping("/{reviewId}/expected-time")
    public ChiefEditorReviewDetailResponse updateExpectedTime(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long reviewId,
            @Valid @RequestBody UpdateExpectedTimeRequest request
    ) {
        return reviewService.updateExpectedTime(principal.user().getId(), reviewId, request);
    }

    @PostMapping("/{reviewId}/decision")
    public ChiefEditorReviewDetailResponse decide(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long reviewId,
            @Valid @RequestBody ReviewDecisionRequest request
    ) {
        return reviewService.decide(principal.user().getId(), reviewId, request);
    }

    @PostMapping("/{reviewId}/annotations")
    public AnnotationResponse createAnnotation(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long reviewId,
            @Valid @RequestBody CreateAnnotationRequest request
    ) {
        return annotationService.create(principal.user().getId(), reviewId, request);
    }

    @PutMapping("/{reviewId}/annotations/{annotationId}")
    public AnnotationResponse updateAnnotation(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long reviewId,
            @PathVariable Long annotationId,
            @Valid @RequestBody UpdateAnnotationRequest request
    ) {
        return annotationService.update(principal.user().getId(), reviewId, annotationId, request);
    }

    @DeleteMapping("/{reviewId}/annotations/{annotationId}")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void deleteAnnotation(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long reviewId,
            @PathVariable Long annotationId
    ) {
        annotationService.delete(principal.user().getId(), reviewId, annotationId);
    }
}

package com.easylang.activitymonitoring.dto;

import com.easylang.activitymonitoring.model.AnnotationKind;
import com.easylang.activitymonitoring.model.ActivityStatus;
import com.easylang.activitymonitoring.model.ReviewKind;
import com.easylang.activitymonitoring.model.ReviewResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ReviewDtos {
    private ReviewDtos() {
    }

    public record ReviewHistoryResponse(List<ReviewCycleResponse> reviews) {
    }

    public record ReviewCycleResponse(
            Long id,
            int cycleNumber,
            ReviewKind kind,
            LocalDate weekStartDate,
            Instant submittedAt,
            ReviewResult result,
            String feedback,
            BigDecimal expectedCheckingHours,
            Integer correctionPages,
            Instant decidedAt,
            long annotationCount,
            DocumentMetadataResponse document,
            List<Integer> correctionPageNumbers
    ) {
    }

    public record UpdateExpectedTimeRequest(
            @NotNull(message = "Expected checking hours are required")
            @DecimalMin(value = "0.01", message = "Expected checking hours must be at least 0.01")
            @DecimalMax(value = "1000.00", message = "Expected checking hours must not exceed 1000")
            @Digits(integer = 4, fraction = 2, message = "Expected checking hours may have at most 2 decimal places")
            BigDecimal expectedCheckingHours
    ) {
    }

    public record ReviewDecisionRequest(
            @NotNull(message = "Review result is required") ReviewResult result,
            String feedback,
            @Min(value = 1, message = "Correction pages must be at least 1")
            @Max(value = 10000, message = "Correction pages must not exceed 10000")
            Integer correctionPages,
            List<@Min(1) @Max(10000) Integer> correctionPageNumbers
    ) {
        public ReviewDecisionRequest(ReviewResult result, String feedback, Integer correctionPages) {
            this(result, feedback, correctionPages, null);
        }
    }

    public record ChiefEditorReviewDetailResponse(
            Long id,
            Long activityId,
            String activityNumber,
            String activityName,
            Long projectId,
            String projectName,
            ActivityStatus activityStatus,
            int cycleNumber,
            ReviewKind kind,
            LocalDate weekStartDate,
            Instant submittedAt,
            ReviewResult result,
            String feedback,
            BigDecimal expectedCheckingHours,
            Integer correctionPages,
            Instant decidedAt,
            DocumentMetadataResponse document,
            List<AnnotationResponse> annotations,
            List<ReviewCycleResponse> previousCycles,
            List<Integer> correctionPageNumbers
    ) {
    }

    public record DocumentMetadataResponse(
            String originalName,
            String contentType,
            long sizeBytes,
            String sha256,
            String downloadUrl,
            String annotatedDownloadUrl
    ) {
    }

    public record AnnotationRect(
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal x,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal y,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal width,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal height
    ) {
    }

    public record CreateAnnotationRequest(
            @NotNull(message = "Annotation page is required")
            @Min(value = 1, message = "Annotation page must be at least 1")
            @Max(value = 10000, message = "Annotation page must not exceed 10000")
            Integer pageNumber,
            @NotNull(message = "Annotation kind is required") AnnotationKind kind,
            @NotNull(message = "Annotation geometry is required")
            @Size(min = 1, max = 20, message = "An annotation must contain between 1 and 20 rectangles")
            List<@Valid AnnotationRect> rects,
            @Size(max = 4000, message = "Quoted text must not exceed 4000 characters") String quotedText,
            @Size(max = 4000, message = "Comment must not exceed 4000 characters") String comment,
            @Size(max = 4000, message = "Suggested replacement must not exceed 4000 characters") String suggestedReplacement
    ) {
    }

    public record UpdateAnnotationRequest(
            @NotNull(message = "Annotation page is required")
            @Min(value = 1, message = "Annotation page must be at least 1")
            @Max(value = 10000, message = "Annotation page must not exceed 10000")
            Integer pageNumber,
            @NotNull(message = "Annotation kind is required") AnnotationKind kind,
            @NotNull(message = "Annotation geometry is required")
            @Size(min = 1, max = 20, message = "An annotation must contain between 1 and 20 rectangles")
            List<@Valid AnnotationRect> rects,
            @Size(max = 4000, message = "Quoted text must not exceed 4000 characters") String quotedText,
            @Size(max = 4000, message = "Comment must not exceed 4000 characters") String comment,
            @Size(max = 4000, message = "Suggested replacement must not exceed 4000 characters") String suggestedReplacement
    ) {
    }

    public record AnnotationResponse(
            Long id,
            int pageNumber,
            AnnotationKind kind,
            List<AnnotationRect> rects,
            String quotedText,
            String comment,
            String suggestedReplacement,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}

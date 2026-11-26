package com.easylang.activitymonitoring.service;

import com.easylang.activitymonitoring.dto.ReviewDtos.DocumentMetadataResponse;
import com.easylang.activitymonitoring.dto.ReviewDtos.ReviewCycleResponse;
import com.easylang.activitymonitoring.dto.ReviewDtos.ReviewHistoryResponse;
import com.easylang.activitymonitoring.model.ActivityStatus;
import com.easylang.activitymonitoring.model.ActivityTranslator;
import com.easylang.activitymonitoring.model.Review;
import com.easylang.activitymonitoring.model.Role;
import com.easylang.activitymonitoring.model.User;
import com.easylang.activitymonitoring.repository.ActivityTranslatorRepository;
import com.easylang.activitymonitoring.repository.ReviewRepository;
import com.easylang.activitymonitoring.storage.DocumentUploadService;
import com.easylang.activitymonitoring.storage.PrivateDocumentStorageService;
import com.easylang.activitymonitoring.storage.UploadedDocument;
import java.io.InputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TranslatorReviewService {

    private final ReviewRepository reviewRepository;
    private final ActivityTranslatorRepository assignmentRepository;
    private final DocumentUploadService documentUploadService;
    private final PrivateDocumentStorageService storage;
    private final ReviewAnnotationService annotationService;

    public TranslatorReviewService(
            ReviewRepository reviewRepository,
            ActivityTranslatorRepository assignmentRepository,
            DocumentUploadService documentUploadService,
            PrivateDocumentStorageService storage,
            ReviewAnnotationService annotationService
    ) {
        this.reviewRepository = reviewRepository;
        this.assignmentRepository = assignmentRepository;
        this.documentUploadService = documentUploadService;
        this.storage = storage;
        this.annotationService = annotationService;
    }

    @Transactional(readOnly = true)
    public ReviewHistoryResponse history(Long translatorId, Long activityId) {
        responsibleAssignment(translatorId, activityId);
        return new ReviewHistoryResponse(reviewRepository.findByActivityIdOrderByCycleNumberDesc(activityId)
                .stream().map(this::toResponse).toList());
    }

    @Transactional
    public ReviewCycleResponse attachLegacyDocument(
            Long translatorId,
            Long activityId,
            Long reviewId,
            MultipartFile file
    ) {
        responsibleAssignment(translatorId, activityId);
        Review review = reviewRepository.findByIdAndActivityIdForUpdate(reviewId, activityId)
                .orElseThrow(() -> notFound("Review not found"));
        if (review.getResult() != null || review.getActivity().getStatus() != ActivityStatus.UNDER_CHECKING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Closed reviews cannot receive attachments");
        }
        if (review.hasDocument()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This review already has a document");
        }

        UploadedDocument document = documentUploadService.validateAndStore(file);
        review.attachDocument(
                document.storageKey(), document.originalName(), document.contentType(), document.sizeBytes(), document.sha256()
        );
        reviewRepository.saveAndFlush(review);
        return toResponse(review);
    }

    @Transactional(readOnly = true)
    public ReviewDocumentDownload download(User user, Long reviewId) {
        Review review = reviewRepository.findByIdWithContext(reviewId)
                .orElseThrow(() -> notFound("Review not found"));
        authorizeDownload(user, review);
        if (!review.hasDocument()) {
            throw notFound("Review document not found");
        }
        InputStream content = storage.open(review.getDocumentStorageKey());
        return new ReviewDocumentDownload(
                review.getDocumentOriginalName(),
                review.getDocumentContentType(),
                review.getDocumentSizeBytes(),
                content
        );
    }

    private void authorizeDownload(User user, Review review) {
        if (user.getRole() == Role.TRANSLATOR) {
            responsibleAssignment(user.getId(), review.getActivity().getId());
            return;
        }
        if (user.getRole() == Role.CHIEF_EDITOR && review.getChiefEditor().getId().equals(user.getId())) {
            return;
        }
        throw notFound("Review not found");
    }

    private ActivityTranslator responsibleAssignment(Long translatorId, Long activityId) {
        ActivityTranslator assignment = assignmentRepository
                .findByActivityIdAndTranslatorId(activityId, translatorId)
                .orElseThrow(() -> notFound("Activity not found"));
        if (!assignment.isResponsible()) {
            throw notFound("Activity not found");
        }
        return assignment;
    }

    private ReviewCycleResponse toResponse(Review review) {
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
                annotationService.count(review.getId()),
                document,
                review.getCorrectionPageNumbers()
        );
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    public record ReviewDocumentDownload(
            String originalName,
            String contentType,
            long sizeBytes,
            InputStream content
    ) {
    }
}

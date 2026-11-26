package com.easylang.activitymonitoring.service;

import com.easylang.activitymonitoring.dto.ReviewDtos.AnnotationRect;
import com.easylang.activitymonitoring.dto.ReviewDtos.AnnotationResponse;
import com.easylang.activitymonitoring.dto.ReviewDtos.CreateAnnotationRequest;
import com.easylang.activitymonitoring.dto.ReviewDtos.UpdateAnnotationRequest;
import com.easylang.activitymonitoring.model.Activity;
import com.easylang.activitymonitoring.model.AnnotationKind;
import com.easylang.activitymonitoring.model.Review;
import com.easylang.activitymonitoring.model.ReviewAnnotation;
import com.easylang.activitymonitoring.model.Role;
import com.easylang.activitymonitoring.model.User;
import com.easylang.activitymonitoring.repository.ActivityRepository;
import com.easylang.activitymonitoring.repository.ActivityTranslatorRepository;
import com.easylang.activitymonitoring.repository.ReviewAnnotationRepository;
import com.easylang.activitymonitoring.repository.ReviewRepository;
import com.easylang.activitymonitoring.storage.PrivateDocumentStorageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReviewAnnotationService {

    private static final int MAX_ANNOTATIONS = 500;
    private static final int MAX_TEXT = 4000;

    private final ReviewRepository reviewRepository;
    private final ReviewAnnotationRepository annotationRepository;
    private final ActivityRepository activityRepository;
    private final ActivityTranslatorRepository assignmentRepository;
    private final PrivateDocumentStorageService storage;
    private final ObjectMapper objectMapper;
    private final Clock businessClock;

    public ReviewAnnotationService(
            ReviewRepository reviewRepository,
            ReviewAnnotationRepository annotationRepository,
            ActivityRepository activityRepository,
            ActivityTranslatorRepository assignmentRepository,
            PrivateDocumentStorageService storage,
            ObjectMapper objectMapper,
            Clock businessClock
    ) {
        this.reviewRepository = reviewRepository;
        this.annotationRepository = annotationRepository;
        this.activityRepository = activityRepository;
        this.assignmentRepository = assignmentRepository;
        this.storage = storage;
        this.objectMapper = objectMapper;
        this.businessClock = businessClock;
    }

    @Transactional(readOnly = true)
    public List<AnnotationResponse> listForUser(User user, Long reviewId) {
        Review review = reviewRepository.findByIdWithContext(reviewId)
                .orElseThrow(() -> notFound("Review not found"));
        authorizeRead(user, review);
        return list(reviewId);
    }

    @Transactional
    public AnnotationResponse create(Long chiefEditorId, Long reviewId, CreateAnnotationRequest request) {
        Review review = lockedOwnedReview(chiefEditorId, reviewId);
        requireEditable(review);
        if (annotationRepository.countByReviewId(reviewId) >= MAX_ANNOTATIONS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This review already has the maximum number of annotations");
        }
        Content content = validateContent(
                request.kind(), request.rects(), request.quotedText(), request.comment(), request.suggestedReplacement()
        );
        ReviewAnnotation annotation = annotationRepository.save(new ReviewAnnotation(
                review,
                request.pageNumber(),
                request.kind(),
                content.rectsJson(),
                content.quotedText(),
                content.comment(),
                content.suggestedReplacement(),
                businessClock.instant()
        ));
        return toResponse(annotation);
    }

    @Transactional
    public AnnotationResponse update(Long chiefEditorId, Long reviewId, Long annotationId, UpdateAnnotationRequest request) {
        Review review = lockedOwnedReview(chiefEditorId, reviewId);
        requireEditable(review);
        ReviewAnnotation annotation = annotationRepository.findByIdAndReviewId(annotationId, reviewId)
                .orElseThrow(() -> notFound("Annotation not found"));
        Content content = validateContent(
                request.kind(), request.rects(), request.quotedText(), request.comment(), request.suggestedReplacement()
        );
        annotation.update(
                request.pageNumber(),
                request.kind(),
                content.rectsJson(),
                content.quotedText(),
                content.comment(),
                content.suggestedReplacement(),
                businessClock.instant()
        );
        annotationRepository.saveAndFlush(annotation);
        return toResponse(annotation);
    }

    @Transactional
    public void delete(Long chiefEditorId, Long reviewId, Long annotationId) {
        Review review = lockedOwnedReview(chiefEditorId, reviewId);
        requireEditable(review);
        ReviewAnnotation annotation = annotationRepository.findByIdAndReviewId(annotationId, reviewId)
                .orElseThrow(() -> notFound("Annotation not found"));
        annotationRepository.delete(annotation);
    }

    @Transactional(readOnly = true)
    public AnnotatedDocument annotatedPdf(User user, Long reviewId) {
        Review review = reviewRepository.findByIdWithContext(reviewId)
                .orElseThrow(() -> notFound("Review not found"));
        authorizeRead(user, review);
        if (!review.hasDocument()) {
            throw notFound("Review document not found");
        }
        if (!"application/pdf".equals(review.getDocumentContentType())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Annotated download is only available for PDF documents"
            );
        }
        List<ReviewAnnotation> annotations = annotationRepository.findByReviewIdOrderByPageNumberAscIdAsc(reviewId);
        if (annotations.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This review has no annotations yet");
        }
        try (InputStream input = storage.open(review.getDocumentStorageKey());
             PDDocument document = PDDocument.load(input.readAllBytes())) {
            stamp(document, annotations);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return new AnnotatedDocument(
                    annotatedName(review.getDocumentOriginalName()),
                    output.toByteArray()
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The annotated PDF could not be generated");
        }
    }

    @Transactional(readOnly = true)
    public long count(Long reviewId) {
        return annotationRepository.countByReviewId(reviewId);
    }

    public List<AnnotationResponse> list(Long reviewId) {
        return annotationRepository.findByReviewIdOrderByPageNumberAscIdAsc(reviewId)
                .stream().map(this::toResponse).toList();
    }

    public AnnotationResponse toResponse(ReviewAnnotation annotation) {
        return new AnnotationResponse(
                annotation.getId(),
                annotation.getPageNumber(),
                annotation.getKind(),
                parseRects(annotation.getRectsJson()),
                annotation.getQuotedText(),
                annotation.getComment(),
                annotation.getSuggestedReplacement(),
                annotation.getCreatedAt(),
                annotation.getUpdatedAt()
        );
    }

    private void stamp(PDDocument document, List<ReviewAnnotation> annotations) throws IOException {
        for (ReviewAnnotation annotation : annotations) {
            if (annotation.getPageNumber() > document.getNumberOfPages()) {
                continue;
            }
            PDPage page = document.getPage(annotation.getPageNumber() - 1);
            PDRectangle box = page.getCropBox();
            try (PDPageContentStream content = new PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.APPEND, true, true
            )) {
                for (AnnotationRect rect : parseRects(annotation.getRectsJson())) {
                    float x = box.getLowerLeftX() + rect.x().floatValue() * box.getWidth();
                    float y = box.getLowerLeftY() + (1f - rect.y().floatValue() - rect.height().floatValue()) * box.getHeight();
                    float width = rect.width().floatValue() * box.getWidth();
                    float height = rect.height().floatValue() * box.getHeight();
                    if (width <= 0 || height <= 0) {
                        continue;
                    }
                    switch (annotation.getKind()) {
                        case HIGHLIGHT -> {
                            content.saveGraphicsState();
                            PDExtendedGraphicsState transparency = new PDExtendedGraphicsState();
                            transparency.setNonStrokingAlphaConstant(0.28f);
                            content.setGraphicsStateParameters(transparency);
                            content.setNonStrokingColor(1f, 0.86f, 0.25f);
                            content.addRect(x, y, width, height);
                            content.fill();
                            content.restoreGraphicsState();
                        }
                        case STRIKETHROUGH -> {
                            content.setStrokingColor(0.75f, 0.12f, 0.12f);
                            content.setLineWidth(1.6f);
                            content.moveTo(x, y + height / 2f);
                            content.lineTo(x + width, y + height / 2f);
                            content.stroke();
                        }
                        case RECT -> {
                            content.setStrokingColor(0.78f, 0.15f, 0.12f);
                            content.setLineWidth(1.8f);
                            content.addRect(x, y, width, height);
                            content.stroke();
                        }
                    }
                }
            }
        }
        appendNotes(document, annotations);
    }

    private void appendNotes(PDDocument document, List<ReviewAnnotation> annotations) throws IOException {
        PDType0Font font = loadNoteFont(document);
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        float margin = 50;
        float y = page.getMediaBox().getHeight() - margin;
        PDPageContentStream content = new PDPageContentStream(document, page);
        try {
            content.beginText();
            content.setFont(font, 15);
            content.newLineAtOffset(margin, y);
            content.showText("EasyLang review notes");
            content.endText();
            y -= 24;

            content.beginText();
            content.setFont(font, 9);
            content.newLineAtOffset(margin, y);
            content.showText("Generated from in-app annotations. The original document is unchanged.");
            content.endText();
            y -= 22;

            int index = 1;
            for (ReviewAnnotation annotation : annotations) {
                String heading = index + ". Page " + annotation.getPageNumber() + " - " + annotation.getKind().name().toLowerCase();
                for (String line : wrap(heading, 95)) {
                    if (y < margin + 20) {
                        content.close();
                        page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        content = new PDPageContentStream(document, page);
                        y = page.getMediaBox().getHeight() - margin;
                    }
                    content.beginText();
                    content.setFont(font, 10);
                    content.newLineAtOffset(margin, y);
                    content.showText(line);
                    content.endText();
                    y -= 13;
                }
                List<String> lines = new ArrayList<>();
                if (annotation.getQuotedText() != null) {
                    lines.add("Selected text: " + annotation.getQuotedText());
                }
                if (annotation.getComment() != null) {
                    lines.add("Comment: " + annotation.getComment());
                }
                if (annotation.getSuggestedReplacement() != null) {
                    lines.add("Suggested replacement: " + annotation.getSuggestedReplacement());
                }
                for (String value : lines) {
                    for (String line : wrap(value, 105)) {
                        if (y < margin + 20) {
                            content.close();
                            page = new PDPage(PDRectangle.A4);
                            document.addPage(page);
                            content = new PDPageContentStream(document, page);
                            y = page.getMediaBox().getHeight() - margin;
                        }
                        content.beginText();
                        content.setFont(font, 9);
                        content.newLineAtOffset(margin, y);
                        content.showText(line);
                        content.endText();
                        y -= 12;
                    }
                }
                y -= 8;
                index++;
                if (y < margin + 40 && index <= annotations.size()) {
                    content.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    content = new PDPageContentStream(document, page);
                    y = page.getMediaBox().getHeight() - margin;
                }
            }
        } finally {
            content.close();
        }
    }

    private PDType0Font loadNoteFont(PDDocument document) throws IOException {
        try (InputStream font = ReviewAnnotationService.class.getResourceAsStream("/fonts/NotoSans-Regular.ttf")) {
            if (font == null) {
                throw new IOException("Bundled review font is missing");
            }
            return PDType0Font.load(document, font, true);
        }
    }

    private List<String> wrap(String value, int maxLength) {
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        List<String> lines = new ArrayList<>();
        while (normalized.length() > maxLength) {
            int split = normalized.lastIndexOf(' ', maxLength);
            if (split <= 0) {
                split = maxLength;
            }
            lines.add(normalized.substring(0, split));
            normalized = normalized.substring(split).trim();
        }
        if (!normalized.isEmpty()) {
            lines.add(normalized);
        }
        return lines.isEmpty() ? List.of("") : lines;
    }

    private Content validateContent(
            AnnotationKind kind,
            List<AnnotationRect> rects,
            String quotedText,
            String comment,
            String suggestedReplacement
    ) {
        if (kind == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Annotation kind is required");
        }
        if (rects == null || rects.isEmpty() || rects.size() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An annotation needs between 1 and 20 rectangles");
        }
        for (AnnotationRect rect : rects) {
            if (rect.width().signum() <= 0 || rect.height().signum() <= 0
                    || rect.x().add(rect.width()).compareTo(BigDecimal.ONE) > 0
                    || rect.y().add(rect.height()).compareTo(BigDecimal.ONE) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Annotation geometry is invalid");
            }
        }
        String quoted = normalize(quotedText);
        String body = normalize(comment);
        String replacement = normalize(suggestedReplacement);
        if (quoted == null && body == null && replacement == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Add a comment, selected text, or suggested replacement"
            );
        }
        try {
            return new Content(
                    objectMapper.writeValueAsString(rects),
                    quoted,
                    body,
                    replacement
            );
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Annotation geometry is invalid");
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_TEXT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Annotation text is too long");
        }
        return trimmed;
    }

    private List<AnnotationRect> parseRects(String json) {
        try {
            List<AnnotationRect> rects = objectMapper.readValue(json, new TypeReference<>() {
            });
            return rects == null ? List.of() : rects;
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private Review lockedOwnedReview(Long chiefEditorId, Long reviewId) {
        Long activityId = reviewRepository.findOwnedActivityId(reviewId, chiefEditorId)
                .orElseThrow(() -> notFound("Review not found"));
        Activity activity = activityRepository.findByIdForUpdate(activityId)
                .orElseThrow(() -> notFound("Review not found"));
        Review review = reviewRepository.findByIdAndActivityIdForUpdate(reviewId, activity.getId())
                .orElseThrow(() -> notFound("Review not found"));
        if (!review.getChiefEditor().getId().equals(chiefEditorId)) {
            throw notFound("Review not found");
        }
        return review;
    }

    private void requireEditable(Review review) {
        if (!review.isOpen()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Annotations are locked after the review decision");
        }
        if (!review.hasDocument() || !"application/pdf".equals(review.getDocumentContentType())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "In-app marks are only available for PDF documents"
            );
        }
    }

    private void authorizeRead(User user, Review review) {
        if (user.getRole() == Role.TRANSLATOR) {
            var assignment = assignmentRepository.findByActivityIdAndTranslatorId(review.getActivity().getId(), user.getId())
                    .orElseThrow(() -> notFound("Review not found"));
            if (!assignment.isResponsible()) {
                throw notFound("Review not found");
            }
            return;
        }
        if (user.getRole() == Role.CHIEF_EDITOR && review.getChiefEditor().getId().equals(user.getId())) {
            return;
        }
        throw notFound("Review not found");
    }

    private String annotatedName(String originalName) {
        int dot = originalName.lastIndexOf('.');
        String base = dot > 0 ? originalName.substring(0, dot) : originalName;
        return base + "-annotated.pdf";
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private record Content(
            String rectsJson,
            String quotedText,
            String comment,
            String suggestedReplacement
    ) {
    }

    public record AnnotatedDocument(String originalName, byte[] content) {
    }
}

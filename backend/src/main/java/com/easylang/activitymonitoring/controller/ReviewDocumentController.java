package com.easylang.activitymonitoring.controller;

import com.easylang.activitymonitoring.security.AuthenticatedUser;
import com.easylang.activitymonitoring.service.ReviewAnnotationService;
import com.easylang.activitymonitoring.service.TranslatorReviewService;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewDocumentController {

    private final TranslatorReviewService reviewService;
    private final ReviewAnnotationService annotationService;

    public ReviewDocumentController(
            TranslatorReviewService reviewService,
            ReviewAnnotationService annotationService
    ) {
        this.reviewService = reviewService;
        this.annotationService = annotationService;
    }

    @GetMapping("/{reviewId}/document")
    public ResponseEntity<InputStreamResource> download(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long reviewId
    ) {
        var download = reviewService.download(principal.user(), reviewId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.originalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.sizeBytes())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(download.content()));
    }

    @GetMapping("/{reviewId}/annotations")
    public java.util.List<com.easylang.activitymonitoring.dto.ReviewDtos.AnnotationResponse> annotations(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long reviewId
    ) {
        return annotationService.listForUser(principal.user(), reviewId);
    }

    @GetMapping("/{reviewId}/annotated-document")
    public ResponseEntity<org.springframework.core.io.ByteArrayResource> downloadAnnotated(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long reviewId
    ) {
        var annotated = annotationService.annotatedPdf(principal.user(), reviewId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(annotated.originalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(annotated.content().length)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new org.springframework.core.io.ByteArrayResource(annotated.content()));
    }
}

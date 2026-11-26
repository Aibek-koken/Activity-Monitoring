package com.easylang.activitymonitoring.controller;

import com.easylang.activitymonitoring.dto.ReviewDtos.ReviewCycleResponse;
import com.easylang.activitymonitoring.dto.ReviewDtos.ReviewHistoryResponse;
import com.easylang.activitymonitoring.security.AuthenticatedUser;
import com.easylang.activitymonitoring.service.TranslatorReviewService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/translator/activities/{activityId}/reviews")
public class TranslatorReviewController {

    private final TranslatorReviewService reviewService;

    public TranslatorReviewController(TranslatorReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public ReviewHistoryResponse history(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long activityId
    ) {
        return reviewService.history(principal.user().getId(), activityId);
    }

    @PostMapping(value = "/{reviewId}/legacy-attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReviewCycleResponse attachLegacyDocument(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long activityId,
            @PathVariable Long reviewId,
            @RequestPart("file") MultipartFile file
    ) {
        return reviewService.attachLegacyDocument(principal.user().getId(), activityId, reviewId, file);
    }
}

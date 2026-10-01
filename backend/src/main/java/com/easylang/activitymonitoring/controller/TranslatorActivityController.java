package com.easylang.activitymonitoring.controller;

import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.ActivityDetailResponse;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.ActivityListResponse;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.CreateWorkRecordRequest;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.UpdateWorkRecordRequest;
import com.easylang.activitymonitoring.security.AuthenticatedUser;
import com.easylang.activitymonitoring.service.TranslatorActivityService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/translator/activities")
public class TranslatorActivityController {

    private final TranslatorActivityService translatorActivityService;

    public TranslatorActivityController(TranslatorActivityService translatorActivityService) {
        this.translatorActivityService = translatorActivityService;
    }

    @GetMapping
    public ActivityListResponse listAssignedActivities(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(name = "q", required = false) String query
    ) {
        return translatorActivityService.listAssignedActivities(principal.user().getId(), query);
    }

    @GetMapping("/{activityId}")
    public ActivityDetailResponse getAssignedActivity(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long activityId
    ) {
        return translatorActivityService.getAssignedActivity(principal.user().getId(), activityId);
    }

    @PostMapping("/{activityId}/work-records")
    public ActivityDetailResponse createWorkRecord(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long activityId,
            @Valid @RequestBody CreateWorkRecordRequest request
    ) {
        return translatorActivityService.createWorkRecord(principal.user().getId(), activityId, request);
    }

    @PutMapping("/{activityId}/work-records/{recordId}")
    public ActivityDetailResponse updateWorkRecord(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long activityId,
            @PathVariable Long recordId,
            @Valid @RequestBody UpdateWorkRecordRequest request
    ) {
        return translatorActivityService.updateWorkRecord(principal.user().getId(), activityId, recordId, request);
    }
}

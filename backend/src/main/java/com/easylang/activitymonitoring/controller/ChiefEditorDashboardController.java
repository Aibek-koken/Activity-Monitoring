package com.easylang.activitymonitoring.controller;

import com.easylang.activitymonitoring.dto.ChiefEditorDtos.AssignedProjectListResponse;
import com.easylang.activitymonitoring.dto.ChiefEditorDtos.AvailabilityResponse;
import com.easylang.activitymonitoring.dto.ChiefEditorDtos.ReviewQueueResponse;
import com.easylang.activitymonitoring.dto.ChiefEditorDtos.UpdateAvailabilityRequest;
import com.easylang.activitymonitoring.dto.ChiefEditorDtos.WorkloadResponse;
import com.easylang.activitymonitoring.security.AuthenticatedUser;
import com.easylang.activitymonitoring.service.ChiefEditorDashboardService;
import com.easylang.activitymonitoring.model.ReviewKind;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chief-editor")
public class ChiefEditorDashboardController {

    private final ChiefEditorDashboardService dashboardService;

    public ChiefEditorDashboardController(ChiefEditorDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/projects")
    public AssignedProjectListResponse listAssignedProjects(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return dashboardService.listAssignedProjects(principal.user().getId());
    }

    @GetMapping("/reviews")
    public ReviewQueueResponse listOpenReviews(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "open") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) ReviewKind kind,
            @RequestParam(required = false) String q
    ) {
        return dashboardService.listOpenReviews(principal.user().getId(), status, page, size, projectId, kind, q);
    }

    @GetMapping("/workload")
    public WorkloadResponse getWorkload(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return dashboardService.getWorkload(principal.user().getId());
    }

    @GetMapping("/availability")
    public AvailabilityResponse getAvailability(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return dashboardService.getAvailability(principal.user().getId());
    }

    @PutMapping("/availability")
    public AvailabilityResponse updateAvailability(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdateAvailabilityRequest request
    ) {
        return dashboardService.updateAvailability(principal.user().getId(), request);
    }
}

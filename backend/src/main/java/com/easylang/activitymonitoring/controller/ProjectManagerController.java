package com.easylang.activitymonitoring.controller;

import com.easylang.activitymonitoring.dto.ProjectManagerDtos.DashboardResponse;
import com.easylang.activitymonitoring.dto.ProjectManagerDtos.ReplaceTranslatorRequest;
import com.easylang.activitymonitoring.security.AuthenticatedUser;
import com.easylang.activitymonitoring.service.ProjectManagerService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/project-manager")
public class ProjectManagerController {
    private final ProjectManagerService service;

    public ProjectManagerController(ProjectManagerService service) { this.service = service; }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard(@AuthenticationPrincipal AuthenticatedUser principal) {
        return service.dashboard(principal.user().getId());
    }

    @PutMapping("/activities/{activityId}/responsible-translator")
    public void replaceTranslator(@AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long activityId, @RequestBody ReplaceTranslatorRequest request) {
        if (request.translatorId() == null) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Translator is required");
        service.replaceTranslator(principal.user().getId(), activityId, request);
    }
}

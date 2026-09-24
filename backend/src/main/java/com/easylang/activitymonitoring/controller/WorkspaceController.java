package com.easylang.activitymonitoring.controller;

import com.easylang.activitymonitoring.model.Role;
import com.easylang.activitymonitoring.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

    @GetMapping("/translator")
    public WorkspaceResponse translator(@AuthenticationPrincipal AuthenticatedUser principal) {
        return response(principal, Role.TRANSLATOR, "Your assigned translation activities will appear here.");
    }

    @GetMapping("/chief-editor")
    public WorkspaceResponse chiefEditor(@AuthenticationPrincipal AuthenticatedUser principal) {
        return response(principal, Role.CHIEF_EDITOR, "Translations awaiting review will appear here.");
    }

    @GetMapping("/project-manager")
    public WorkspaceResponse projectManager(@AuthenticationPrincipal AuthenticatedUser principal) {
        return response(principal, Role.PROJECT_MANAGER, "Project health and staffing signals will appear here.");
    }

    private WorkspaceResponse response(AuthenticatedUser principal, Role role, String message) {
        return new WorkspaceResponse(role, principal.user().getFullName(), message);
    }

    public record WorkspaceResponse(Role role, String userName, String message) {
    }
}

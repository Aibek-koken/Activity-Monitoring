package com.easylang.activitymonitoring.controller;

import com.easylang.activitymonitoring.dto.WorkRecordEditDtos.*;
import com.easylang.activitymonitoring.security.AuthenticatedUser;
import com.easylang.activitymonitoring.service.WorkRecordEditService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class WorkRecordEditController {
    private final WorkRecordEditService service;
    public WorkRecordEditController(WorkRecordEditService service) { this.service = service; }

    @PostMapping("/api/v1/translator/activities/{activityId}/work-records/{recordId}/edit-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public Response request(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long activityId,
                            @PathVariable Long recordId, @Valid @RequestBody CreateRequest input) {
        return service.request(user.user().getId(), activityId, recordId, input);
    }

    @GetMapping("/api/v1/translator/work-record-edit-requests")
    public ListResponse translatorList(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.translatorList(user.user().getId());
    }

    @GetMapping("/api/v1/project-manager/work-record-edit-requests")
    public ListResponse managerList(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.managerList(user.user().getId());
    }

    @PostMapping("/api/v1/project-manager/work-record-edit-requests/{requestId}/decision")
    public Response decide(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long requestId,
                           @Valid @RequestBody DecideRequest input) {
        return service.decide(user.user().getId(), requestId, input);
    }
}

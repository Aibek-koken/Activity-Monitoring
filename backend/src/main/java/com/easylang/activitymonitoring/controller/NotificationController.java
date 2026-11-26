package com.easylang.activitymonitoring.controller;

import com.easylang.activitymonitoring.dto.NotificationDtos;
import com.easylang.activitymonitoring.security.AuthenticatedUser;
import com.easylang.activitymonitoring.service.NotificationHistoryService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationHistoryService service;
    public NotificationController(NotificationHistoryService service) { this.service = service; }
    @GetMapping
    public NotificationDtos.Page list(@AuthenticationPrincipal AuthenticatedUser user,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return service.list(user.user().getId(), page, size);
    }
    @PatchMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        service.markRead(user.user().getId(), id);
    }
    @PatchMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(@AuthenticationPrincipal AuthenticatedUser user) {
        service.markAllRead(user.user().getId());
    }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        service.delete(user.user().getId(), id);
    }
}

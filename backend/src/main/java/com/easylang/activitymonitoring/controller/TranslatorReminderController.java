package com.easylang.activitymonitoring.controller;

import com.easylang.activitymonitoring.dto.ReminderDtos.ReminderListResponse;
import com.easylang.activitymonitoring.security.AuthenticatedUser;
import com.easylang.activitymonitoring.service.ReminderService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/translator/reminders")
public class TranslatorReminderController {

    private final ReminderService reminderService;

    public TranslatorReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @GetMapping
    public ReminderListResponse list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return reminderService.listVisible(principal.user().getId());
    }

    @PatchMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long notificationId) {
        reminderService.markRead(principal.user().getId(), notificationId);
    }
}

package com.easylang.activitymonitoring.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class NotificationTest {

    @Test
    void correctionNotificationDedupeKeyIsBoundToTheReviewCycle() {
        User manager = new User("manager@example.test", "PM", "Project Manager", "unused", Role.PROJECT_MANAGER);
        User translator = new User("translator@example.test", "TR", "Translator", "unused", Role.TRANSLATOR);
        Project project = new Project("Review project", manager, LocalDate.of(2026, 11, 10));
        Activity activity = new Activity(
                "EL-NOTIFICATION", "Correction notification", project,
                ActivityStatus.CORRECTION_REQUIRED, LocalDate.of(2026, 11, 10)
        );

        Notification notification = Notification.correctionRequested(
                translator, activity, 42L, LocalDate.of(2026, 11, 10),
                "Corrections requested", Instant.parse("2026-11-10T03:00:00Z")
        );

        assertEquals(NotificationType.CORRECTION_REQUESTED, notification.getType());
        assertEquals("review:42:correction-requested", notification.getDedupeKey());
    }
}

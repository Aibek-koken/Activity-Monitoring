package com.easylang.activitymonitoring.config;

import com.easylang.activitymonitoring.service.ReminderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.reminders.scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);
    private final ReminderService reminderService;

    public ReminderScheduler(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Scheduled(cron = "0 0 * * * *", zone = "${app.business-zone:Asia/Almaty}")
    public void evaluateHourly() {
        try {
            reminderService.evaluateDueReminders();
        } catch (RuntimeException exception) {
            log.error("Reminder evaluation failed; the next hourly run will retry", exception);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void evaluateOnStartup() {
        evaluateHourly();
    }
}

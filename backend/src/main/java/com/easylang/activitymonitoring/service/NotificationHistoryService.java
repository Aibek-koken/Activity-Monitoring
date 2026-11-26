package com.easylang.activitymonitoring.service;

import com.easylang.activitymonitoring.dto.NotificationDtos;
import com.easylang.activitymonitoring.model.Notification;
import com.easylang.activitymonitoring.model.NotificationType;
import com.easylang.activitymonitoring.model.ActivityStatus;
import com.easylang.activitymonitoring.repository.NotificationRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationHistoryService {
    private final NotificationRepository repository;
    private final Clock clock;
    public NotificationHistoryService(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public NotificationDtos.Page list(Long userId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid notification page");
        }
        var result = repository.findHistory(userId, PageRequest.of(page, size));
        var items = result.getContent().stream().map(this::toItem).toList();
        LocalDate today = LocalDate.now(clock);
        long unread = repository.countActiveUnread(userId, today,
                today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
        return new NotificationDtos.Page(items, result.getNumber(), result.getTotalPages(),
                result.getTotalElements(), unread);
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        Notification notification = owned(userId, id);
        notification.markRead(clock.instant());
    }

    @Transactional
    public void markAllRead(Long userId) {
        repository.markAllRead(userId, clock.instant());
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Notification notification = owned(userId, id);
        notification.delete(clock.instant());
    }

    private Notification owned(Long userId, Long id) {
        Notification notification = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (notification.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
        }
        return notification;
    }

    private NotificationDtos.Item toItem(Notification notification) {
        return new NotificationDtos.Item(notification.getId(), notification.getType(), notification.getMessage(),
                notification.getActivity() == null ? null : notification.getActivity().getId(),
                notification.getActivity() == null ? null : notification.getActivity().getActivityNumber(),
                notification.getCreatedAt(), notification.getReadAt(), actionable(notification));
    }

    private boolean actionable(Notification notification) {
        LocalDate today = LocalDate.now(clock);
        NotificationType type = notification.getType();
        if (type == NotificationType.DAILY_RECORD_REMINDER) return notification.getPeriodDate().equals(today);
        if (type == NotificationType.WEEKLY_ESTIMATE_REMINDER || type == NotificationType.WEEKLY_SUBMISSION_REMINDER) {
            return notification.getPeriodDate().equals(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
                    && (notification.getActivity().getStatus() == ActivityStatus.ASSIGNED
                        || notification.getActivity().getStatus() == ActivityStatus.IN_PROGRESS);
        }
        if (type == NotificationType.CORRECTION_REQUESTED) {
            return notification.getActivity().getStatus() == ActivityStatus.CORRECTION_REQUIRED;
        }
        return true;
    }
}

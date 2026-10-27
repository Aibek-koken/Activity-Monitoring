package com.easylang.activitymonitoring.service;

import com.easylang.activitymonitoring.dto.ReminderDtos.ReminderListResponse;
import com.easylang.activitymonitoring.dto.ReminderDtos.ReminderResponse;
import com.easylang.activitymonitoring.model.ActivityStatus;
import com.easylang.activitymonitoring.model.ActivityTranslator;
import com.easylang.activitymonitoring.model.Notification;
import com.easylang.activitymonitoring.model.NotificationType;
import com.easylang.activitymonitoring.model.Role;
import com.easylang.activitymonitoring.model.User;
import com.easylang.activitymonitoring.repository.ActivityTranslatorRepository;
import com.easylang.activitymonitoring.repository.NotificationRepository;
import com.easylang.activitymonitoring.repository.UserRepository;
import com.easylang.activitymonitoring.repository.WeeklyEstimateRepository;
import com.easylang.activitymonitoring.repository.WorkRecordRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);
    private static final List<ActivityStatus> ACTIVE_STATUSES = List.of(ActivityStatus.ASSIGNED, ActivityStatus.IN_PROGRESS);

    private final ActivityTranslatorRepository assignmentRepository;
    private final WorkRecordRepository recordRepository;
    private final WeeklyEstimateRepository estimateRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationWriter notificationWriter;
    private final Clock businessClock;

    public ReminderService(ActivityTranslatorRepository assignmentRepository,
                           WorkRecordRepository recordRepository,
                           WeeklyEstimateRepository estimateRepository,
                           NotificationRepository notificationRepository,
                           UserRepository userRepository,
                           NotificationWriter notificationWriter,
                           Clock businessClock) {
        this.assignmentRepository = assignmentRepository;
        this.recordRepository = recordRepository;
        this.estimateRepository = estimateRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.notificationWriter = notificationWriter;
        this.businessClock = businessClock;
    }

    public void evaluateDueReminders() {
        LocalDate today = LocalDate.now(businessClock);
        LocalTime time = LocalTime.now(businessClock);
        DayOfWeek day = today.getDayOfWeek();

        if (day.getValue() <= DayOfWeek.FRIDAY.getValue() && !time.isBefore(LocalTime.of(18, 0))) {
            createDailyReminders(today);
        }
        if (day == DayOfWeek.FRIDAY && !time.isBefore(LocalTime.of(16, 0))) {
            createWeeklyReminders(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
        }
    }

    private void createDailyReminders(LocalDate today) {
        Set<Long> recordedIds = Set.copyOf(recordRepository.findTranslatorIdsWithRecordsOn(today));
        List<Long> missingIds = assignmentRepository.findActiveTranslatorIds(ACTIVE_STATUSES, Role.TRANSLATOR, today)
                .stream().filter(id -> !recordedIds.contains(id)).toList();
        Map<Long, User> users = userRepository.findAllById(missingIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Set<String> existingKeys = Set.copyOf(notificationRepository.findDedupeKeys(
                NotificationType.DAILY_RECORD_REMINDER, today
        ));

        for (Long id : missingIds) {
            User user = users.get(id);
            if (user != null) {
                persistIfNew(new Notification(user, null, NotificationType.DAILY_RECORD_REMINDER, today,
                        "Record today's translated volume for your active work.", businessClock.instant()), existingKeys);
            }
        }
    }

    private void createWeeklyReminders(LocalDate weekStart) {
        Set<Long> estimatedIds = Set.copyOf(estimateRepository.findActivityIdsEstimatedForWeek(weekStart));
        Set<String> existingKeys = Set.copyOf(notificationRepository.findDedupeKeys(
                NotificationType.WEEKLY_ESTIMATE_REMINDER, weekStart
        ));
        LocalDate today = LocalDate.now(businessClock);
        for (ActivityTranslator assignment : assignmentRepository.findActiveResponsibleAssignments(
                ACTIVE_STATUSES, Role.TRANSLATOR, today
        )) {
            if (!estimatedIds.contains(assignment.getActivity().getId())) {
                persistIfNew(new Notification(
                        assignment.getTranslator(), assignment.getActivity(),
                        NotificationType.WEEKLY_ESTIMATE_REMINDER, weekStart,
                        "Add this week's remaining-volume estimate for " + assignment.getActivity().getActivityNumber() + ".",
                        businessClock.instant()
                ), existingKeys);
            }
        }
    }

    private void persistIfNew(Notification notification, Set<String> existingKeys) {
        if (existingKeys.contains(notification.getDedupeKey())) return;
        try {
            notificationWriter.insert(notification);
        } catch (DataIntegrityViolationException exception) {
            if (!notificationRepository.existsByDedupeKey(notification.getDedupeKey())) throw exception;
            log.debug("Reminder already created by another evaluator: {}", notification.getDedupeKey());
        }
    }

    @Transactional(readOnly = true)
    public ReminderListResponse listVisible(Long translatorId) {
        LocalDate today = LocalDate.now(businessClock);
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        boolean hasActiveAssignment = assignmentRepository.hasActiveAssignment(translatorId, ACTIVE_STATUSES, today);
        boolean recordedToday = recordRepository.existsByTranslatorIdAndRecordDate(translatorId, today);
        Set<Long> activeResponsibleIds = Set.copyOf(assignmentRepository.findActiveResponsibleActivityIds(
                translatorId, ACTIVE_STATUSES, today
        ));
        Set<Long> estimatedIds = Set.copyOf(estimateRepository.findActivityIdsEstimatedForWeek(weekStart));

        List<ReminderResponse> reminders = notificationRepository.findUnreadByUser(translatorId).stream()
                .filter(notification -> notification.getType() == NotificationType.DAILY_RECORD_REMINDER
                        ? notification.getPeriodDate().equals(today) && hasActiveAssignment && !recordedToday
                        : notification.getPeriodDate().equals(weekStart)
                                && notification.getActivity() != null
                                && activeResponsibleIds.contains(notification.getActivity().getId())
                                && !estimatedIds.contains(notification.getActivity().getId()))
                .map(this::toResponse)
                .toList();
        return new ReminderListResponse(reminders);
    }

    @Transactional
    public void markRead(Long translatorId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, translatorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reminder not found"));
        notification.markRead(businessClock.instant());
    }

    private ReminderResponse toResponse(Notification notification) {
        return new ReminderResponse(
                notification.getId(), notification.getType(), notification.getPeriodDate(),
                notification.getMessage(),
                notification.getActivity() == null ? null : notification.getActivity().getId(),
                notification.getActivity() == null ? null : notification.getActivity().getActivityNumber(),
                notification.getCreatedAt()
        );
    }
}

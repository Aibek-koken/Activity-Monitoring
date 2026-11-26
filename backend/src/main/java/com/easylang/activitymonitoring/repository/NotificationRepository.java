package com.easylang.activitymonitoring.repository;

import com.easylang.activitymonitoring.model.Notification;
import com.easylang.activitymonitoring.model.NotificationType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    boolean existsByDedupeKey(String dedupeKey);
    Optional<Notification> findByDedupeKey(String dedupeKey);

    @Modifying
    @Query("update Notification n set n.read = true, n.readAt = :now where n.user.id = :userId and n.deletedAt is null and n.read = false")
    int markAllRead(@Param("userId") Long userId, @Param("now") java.time.Instant now);

    @Query("""
            select notification
            from Notification notification
            left join fetch notification.activity
            where notification.user.id = :userId and notification.read = false and notification.deletedAt is null
            order by notification.createdAt desc
            """)
    List<Notification> findUnreadByUser(@Param("userId") Long userId);

    @Query("""
            select notification.dedupeKey
            from Notification notification
            where notification.type = :type and notification.periodDate = :periodDate
            """)
    List<String> findDedupeKeys(@Param("type") NotificationType type, @Param("periodDate") LocalDate periodDate);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    @Query(value = "select n from Notification n left join fetch n.activity where n.user.id = :userId and n.deletedAt is null order by n.createdAt desc, n.id desc",
            countQuery = "select count(n) from Notification n where n.user.id = :userId and n.deletedAt is null")
    Page<Notification> findHistory(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            select count(n) from Notification n left join n.activity activity
            where n.user.id = :userId and n.read = false and n.deletedAt is null
              and (
                (n.type = com.easylang.activitymonitoring.model.NotificationType.DAILY_RECORD_REMINDER and n.periodDate = :today)
                or (n.type in (com.easylang.activitymonitoring.model.NotificationType.WEEKLY_ESTIMATE_REMINDER,
                               com.easylang.activitymonitoring.model.NotificationType.WEEKLY_SUBMISSION_REMINDER)
                    and n.periodDate = :weekStart
                    and activity.status in (com.easylang.activitymonitoring.model.ActivityStatus.ASSIGNED,
                                              com.easylang.activitymonitoring.model.ActivityStatus.IN_PROGRESS))
                or (n.type = com.easylang.activitymonitoring.model.NotificationType.CORRECTION_REQUESTED
                    and activity.status = com.easylang.activitymonitoring.model.ActivityStatus.CORRECTION_REQUIRED)
                or n.type in (com.easylang.activitymonitoring.model.NotificationType.RECORD_EDIT_REQUESTED,
                              com.easylang.activitymonitoring.model.NotificationType.RECORD_EDIT_APPROVED,
                              com.easylang.activitymonitoring.model.NotificationType.RECORD_EDIT_REJECTED)
              )
            """)
    long countActiveUnread(@Param("userId") Long userId, @Param("today") LocalDate today,
                           @Param("weekStart") LocalDate weekStart);
}

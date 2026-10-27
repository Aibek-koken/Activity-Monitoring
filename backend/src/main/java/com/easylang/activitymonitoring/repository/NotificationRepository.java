package com.easylang.activitymonitoring.repository;

import com.easylang.activitymonitoring.model.Notification;
import com.easylang.activitymonitoring.model.NotificationType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    boolean existsByDedupeKey(String dedupeKey);

    @Query("""
            select notification
            from Notification notification
            left join fetch notification.activity
            where notification.user.id = :userId and notification.read = false
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
}

package com.easylang.activitymonitoring.repository;

import com.easylang.activitymonitoring.model.Notification;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    boolean existsByDedupeKey(String dedupeKey);

    List<Notification> findByUserIdAndReadFalseOrderByCreatedAtDesc(Long userId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);
}

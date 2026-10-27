package com.easylang.activitymonitoring.service;

import com.easylang.activitymonitoring.model.Notification;
import com.easylang.activitymonitoring.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationWriter {

    private final NotificationRepository notificationRepository;

    public NotificationWriter(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(Notification notification) {
        notificationRepository.saveAndFlush(notification);
    }
}

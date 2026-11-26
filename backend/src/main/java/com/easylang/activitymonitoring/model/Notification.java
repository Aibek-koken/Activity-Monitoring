package com.easylang.activitymonitoring.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id")
    private Activity activity;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private NotificationType type;

    @Column(name = "period_date", nullable = false)
    private LocalDate periodDate;

    @Column(name = "dedupe_key", nullable = false, unique = true, length = 160)
    private String dedupeKey;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Notification() {
    }

    public Notification(User user, Activity activity, NotificationType type, LocalDate periodDate,
                        String message, Instant createdAt) {
        if ((type == NotificationType.DAILY_RECORD_REMINDER) != (activity == null)) {
            throw new IllegalArgumentException(
                    "Daily reminders must be user-level and activity notifications must reference an activity"
            );
        }
        this.user = user;
        this.activity = activity;
        this.type = type;
        this.periodDate = periodDate;
        this.dedupeKey = user.getId() + ":" + type + ":" + periodDate + ":" + (activity == null ? "-" : activity.getId());
        this.message = message;
        this.createdAt = createdAt;
    }

    public static Notification correctionRequested(
            User user,
            Activity activity,
            long reviewId,
            LocalDate periodDate,
            String message,
            Instant createdAt
    ) {
        if (reviewId <= 0) {
            throw new IllegalArgumentException("Review id must be positive");
        }
        Notification notification = new Notification(
                user, activity, NotificationType.CORRECTION_REQUESTED, periodDate, message, createdAt
        );
        notification.dedupeKey = "review:" + reviewId + ":correction-requested";
        return notification;
    }

    public void markRead(Instant now) {
        if (!read) {
            read = true;
            readAt = now;
        }
    }

    public void delete(Instant now) {
        deletedAt = now;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public static Notification recordEdit(User user, Activity activity, NotificationType type,
                                          long requestId, LocalDate day, String message, Instant now) {
        Notification notification = new Notification(user, activity, type, day, message, now);
        notification.dedupeKey = "record-edit:" + requestId + ":" + type;
        return notification;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Activity getActivity() {
        return activity;
    }

    public NotificationType getType() {
        return type;
    }

    public LocalDate getPeriodDate() {
        return periodDate;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isRead() {
        return read;
    }

    public Instant getReadAt() {
        return readAt;
    }
}

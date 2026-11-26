package com.easylang.activitymonitoring.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "chief_editor_availability")
public class ChiefEditorAvailability {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "weekly_hours", nullable = false, precision = 6, scale = 2)
    private BigDecimal weeklyHours;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ChiefEditorAvailability() {
    }

    public ChiefEditorAvailability(Long userId, BigDecimal weeklyHours, Instant updatedAt) {
        this.userId = userId;
        this.weeklyHours = weeklyHours;
        this.updatedAt = updatedAt;
    }

    public void updateWeeklyHours(BigDecimal weeklyHours, Instant updatedAt) {
        this.weeklyHours = weeklyHours;
        this.updatedAt = updatedAt;
    }

    public Long getUserId() {
        return userId;
    }

    public BigDecimal getWeeklyHours() {
        return weeklyHours;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

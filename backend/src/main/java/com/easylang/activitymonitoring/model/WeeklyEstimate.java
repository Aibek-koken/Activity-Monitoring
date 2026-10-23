package com.easylang.activitymonitoring.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "weekly_estimates")
public class WeeklyEstimate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "translator_id", nullable = false)
    private User translator;

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(name = "remaining_text_volume", nullable = false, precision = 14, scale = 2)
    private BigDecimal remainingTextVolume;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WeeklyEstimate() {
    }

    public WeeklyEstimate(Activity activity, User translator, LocalDate weekStartDate,
                          BigDecimal remainingTextVolume, Instant now) {
        this.activity = activity;
        this.translator = translator;
        this.weekStartDate = weekStartDate;
        this.remainingTextVolume = remainingTextVolume;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateRemainingTextVolume(BigDecimal remainingTextVolume, Instant now) {
        this.remainingTextVolume = remainingTextVolume;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Activity getActivity() {
        return activity;
    }

    public User getTranslator() {
        return translator;
    }

    public LocalDate getWeekStartDate() {
        return weekStartDate;
    }

    public BigDecimal getRemainingTextVolume() {
        return remainingTextVolume;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

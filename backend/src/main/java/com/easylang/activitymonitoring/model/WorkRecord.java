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
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "work_records",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_work_records_activity_translator_date",
                columnNames = {"activity_id", "translator_id", "record_date"}
        )
)
public class WorkRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "translator_id", nullable = false)
    private User translator;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    @Column(name = "translated_volume", nullable = false, precision = 10, scale = 2)
    private BigDecimal translatedVolume;

    @Column(name = "work_hours", precision = 5, scale = 2)
    private BigDecimal workHours;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected WorkRecord() {
    }

    public WorkRecord(
            Activity activity,
            User translator,
            LocalDate recordDate,
            BigDecimal translatedVolume,
            BigDecimal workHours
    ) {
        this.activity = activity;
        this.translator = translator;
        this.recordDate = recordDate;
        this.translatedVolume = translatedVolume;
        this.workHours = workHours;
    }

    public void updateWork(BigDecimal translatedVolume, BigDecimal workHours) {
        this.translatedVolume = translatedVolume;
        this.workHours = workHours;
        this.updatedAt = Instant.now();
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

    public LocalDate getRecordDate() {
        return recordDate;
    }

    public BigDecimal getTranslatedVolume() {
        return translatedVolume;
    }

    public BigDecimal getWorkHours() {
        return workHours;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

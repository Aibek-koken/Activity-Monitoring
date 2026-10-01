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
import java.time.LocalDate;

@Entity
@Table(
        name = "activity_translators",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_activity_translator",
                columnNames = {"activity_id", "translator_id"}
        )
)
public class ActivityTranslator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "translator_id", nullable = false)
    private User translator;

    @Column(name = "is_responsible", nullable = false)
    private boolean responsible;

    @Column(name = "assigned_date", nullable = false)
    private LocalDate assignedDate;

    protected ActivityTranslator() {
    }

    public ActivityTranslator(Activity activity, User translator, boolean responsible, LocalDate assignedDate) {
        this.activity = activity;
        this.translator = translator;
        this.responsible = responsible;
        this.assignedDate = assignedDate;
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

    public boolean isResponsible() {
        return responsible;
    }

    public LocalDate getAssignedDate() {
        return assignedDate;
    }
}

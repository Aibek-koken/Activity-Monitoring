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
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chief_editor_id", nullable = false)
    private User chiefEditor;

    @Column(name = "cycle_number", nullable = false)
    private int cycleNumber;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "checking_date")
    private LocalDate checkingDate;

    @Column(name = "result", length = 32)
    private String result;

    @Column(name = "mistakes_notes", columnDefinition = "text")
    private String mistakesNotes;

    protected Review() {
    }

    public Review(Activity activity, User chiefEditor, int cycleNumber, Instant submittedAt) {
        this.activity = activity;
        this.chiefEditor = chiefEditor;
        this.cycleNumber = cycleNumber;
        this.submittedAt = submittedAt;
    }

    public Long getId() {
        return id;
    }

    public Activity getActivity() {
        return activity;
    }

    public User getChiefEditor() {
        return chiefEditor;
    }

    public int getCycleNumber() {
        return cycleNumber;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public LocalDate getCheckingDate() {
        return checkingDate;
    }

    public String getResult() {
        return result;
    }

    public String getMistakesNotes() {
        return mistakesNotes;
    }
}

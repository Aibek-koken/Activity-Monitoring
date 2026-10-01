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
import java.time.LocalDate;

@Entity
@Table(name = "activities")
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_number", nullable = false, unique = true, length = 40)
    private String activityNumber;

    @Column(name = "activity_name", nullable = false, length = 220)
    private String activityName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ActivityStatus status;

    @Column(name = "created_date", nullable = false)
    private LocalDate createdDate;

    protected Activity() {
    }

    public Activity(
            String activityNumber,
            String activityName,
            Project project,
            ActivityStatus status,
            LocalDate createdDate
    ) {
        this.activityNumber = activityNumber;
        this.activityName = activityName;
        this.project = project;
        this.status = status;
        this.createdDate = createdDate;
    }

    public Long getId() {
        return id;
    }

    public String getActivityNumber() {
        return activityNumber;
    }

    public String getActivityName() {
        return activityName;
    }

    public Project getProject() {
        return project;
    }

    public ActivityStatus getStatus() {
        return status;
    }

    public void markInProgress() {
        if (status == ActivityStatus.ASSIGNED) {
            status = ActivityStatus.IN_PROGRESS;
        }
    }

    public LocalDate getCreatedDate() {
        return createdDate;
    }
}

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
import java.time.LocalDate;

@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_name", nullable = false, unique = true, length = 180)
    private String projectName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_manager_id", nullable = false)
    private User projectManager;

    @Column(name = "created_date", nullable = false)
    private LocalDate createdDate;

    protected Project() {
    }

    public Project(String projectName, User projectManager, LocalDate createdDate) {
        this.projectName = projectName;
        this.projectManager = projectManager;
        this.createdDate = createdDate;
    }

    public Long getId() {
        return id;
    }

    public String getProjectName() {
        return projectName;
    }

    public User getProjectManager() {
        return projectManager;
    }

    public LocalDate getCreatedDate() {
        return createdDate;
    }
}

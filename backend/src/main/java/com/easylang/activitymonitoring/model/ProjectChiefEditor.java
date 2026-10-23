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

@Entity
@Table(name = "project_chief_editors")
public class ProjectChiefEditor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chief_editor_id", nullable = false)
    private User chiefEditor;

    @Column(name = "is_responsible", nullable = false)
    private boolean responsible;

    // A nullable unique key permits many assisting editors but one responsible editor per project.
    @Column(name = "responsible_project_id")
    private Long responsibleProjectId;

    protected ProjectChiefEditor() {
    }

    public ProjectChiefEditor(Project project, User chiefEditor, boolean responsible) {
        this.project = project;
        this.chiefEditor = chiefEditor;
        this.responsible = responsible;
        this.responsibleProjectId = responsible ? project.getId() : null;
    }

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public User getChiefEditor() {
        return chiefEditor;
    }

    public boolean isResponsible() {
        return responsible;
    }
}

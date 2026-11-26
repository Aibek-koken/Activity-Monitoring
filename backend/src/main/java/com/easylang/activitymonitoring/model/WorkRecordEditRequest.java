package com.easylang.activitymonitoring.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "work_record_edit_requests")
public class WorkRecordEditRequest {
    public enum Status { PENDING, APPROVED, REJECTED, USED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "work_record_id")
    private WorkRecord workRecord;
    @Column(name = "active_work_record_id")
    private Long activeWorkRecordId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "translator_id")
    private User translator;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "project_manager_id")
    private User projectManager;
    @Column(nullable = false, length = 1000)
    private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;
    @Column(name = "decision_comment", length = 1000)
    private String decisionComment;
    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;
    @Column(name = "decided_at")
    private Instant decidedAt;
    @Column(name = "used_at")
    private Instant usedAt;

    protected WorkRecordEditRequest() { }

    public WorkRecordEditRequest(WorkRecord workRecord, User translator, User projectManager,
                                 String reason, Instant requestedAt) {
        this.workRecord = workRecord;
        this.activeWorkRecordId = workRecord.getId();
        this.translator = translator;
        this.projectManager = projectManager;
        this.reason = reason;
        this.requestedAt = requestedAt;
    }

    public void decide(boolean approve, String comment, Instant at) {
        if (status != Status.PENDING) throw new IllegalStateException("Request already decided");
        status = approve ? Status.APPROVED : Status.REJECTED;
        if (!approve) activeWorkRecordId = null;
        decisionComment = comment;
        decidedAt = at;
    }

    public void use(Instant at) {
        if (status != Status.APPROVED) throw new IllegalStateException("Request is not approved");
        status = Status.USED;
        activeWorkRecordId = null;
        usedAt = at;
    }

    public Long getId() { return id; }
    public WorkRecord getWorkRecord() { return workRecord; }
    public User getTranslator() { return translator; }
    public User getProjectManager() { return projectManager; }
    public String getReason() { return reason; }
    public Status getStatus() { return status; }
    public String getDecisionComment() { return decisionComment; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getDecidedAt() { return decidedAt; }
}

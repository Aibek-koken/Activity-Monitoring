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
import java.math.BigDecimal;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "review_kind", nullable = false, length = 16)
    private ReviewKind kind = ReviewKind.FINAL;

    @Column(name = "week_start_date")
    private LocalDate weekStartDate;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "checking_date")
    private LocalDate checkingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", length = 32)
    private ReviewResult result;

    @Column(name = "mistakes_notes", columnDefinition = "text")
    private String mistakesNotes;

    @Column(name = "document_storage_key", unique = true, length = 160)
    private String documentStorageKey;

    @Column(name = "document_original_name", length = 255)
    private String documentOriginalName;

    @Column(name = "document_content_type", length = 100)
    private String documentContentType;

    @Column(name = "document_size_bytes")
    private Long documentSizeBytes;

    @Column(name = "document_sha256", length = 64)
    private String documentSha256;

    @Column(name = "expected_checking_hours", precision = 6, scale = 2)
    private BigDecimal expectedCheckingHours;

    @Column(name = "correction_pages")
    private Integer correctionPages;

    @Column(name = "correction_page_numbers", columnDefinition = "text")
    private String correctionPageNumbers;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "open_slot")
    private Short openSlot = 1;

    protected Review() {
    }

    public Review(Activity activity, User chiefEditor, int cycleNumber, Instant submittedAt) {
        this(activity, chiefEditor, cycleNumber, ReviewKind.FINAL, null, submittedAt);
    }

    public Review(
            Activity activity,
            User chiefEditor,
            int cycleNumber,
            ReviewKind kind,
            LocalDate weekStartDate,
            Instant submittedAt
    ) {
        this.activity = activity;
        this.chiefEditor = chiefEditor;
        this.cycleNumber = cycleNumber;
        this.kind = kind;
        this.weekStartDate = weekStartDate;
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

    public ReviewKind getKind() {
        return kind;
    }

    public LocalDate getWeekStartDate() {
        return weekStartDate;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public LocalDate getCheckingDate() {
        return checkingDate;
    }

    public ReviewResult getResult() {
        return result;
    }

    public String getMistakesNotes() {
        return mistakesNotes;
    }

    public String getDocumentStorageKey() {
        return documentStorageKey;
    }

    public String getDocumentOriginalName() {
        return documentOriginalName;
    }

    public String getDocumentContentType() {
        return documentContentType;
    }

    public Long getDocumentSizeBytes() {
        return documentSizeBytes;
    }

    public String getDocumentSha256() {
        return documentSha256;
    }

    public boolean hasDocument() {
        return documentStorageKey != null;
    }

    public void attachDocument(
            String storageKey,
            String originalName,
            String contentType,
            long sizeBytes,
            String sha256
    ) {
        requireOpen();
        if (hasDocument()) {
            throw new IllegalStateException("A document is already attached to this review");
        }
        this.documentStorageKey = storageKey;
        this.documentOriginalName = originalName;
        this.documentContentType = contentType;
        this.documentSizeBytes = sizeBytes;
        this.documentSha256 = sha256;
    }

    public BigDecimal getExpectedCheckingHours() {
        return expectedCheckingHours;
    }

    public Integer getCorrectionPages() {
        return correctionPages;
    }

    public java.util.List<Integer> getCorrectionPageNumbers() {
        if (correctionPageNumbers == null || correctionPageNumbers.isBlank()) return java.util.List.of();
        return java.util.Arrays.stream(correctionPageNumbers.split(",")).map(Integer::valueOf).toList();
    }

    public void setCorrectionPageNumbers(java.util.List<Integer> numbers) {
        correctionPageNumbers = numbers == null || numbers.isEmpty() ? null
                : numbers.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Short getOpenSlot() {
        return openSlot;
    }

    public boolean isOpen() {
        return result == null;
    }

    public void schedule(BigDecimal hours) {
        requireOpen();
        if (hours == null || hours.signum() <= 0 || hours.compareTo(new BigDecimal("1000.00")) > 0) {
            throw new IllegalArgumentException("Expected checking hours must be between 0 and 1000");
        }
        expectedCheckingHours = hours;
    }

    public void close(ReviewResult decision, String feedback, Integer pagesToRedo,
                      Instant decisionTime, LocalDate decisionDate) {
        requireOpen();
        if (decision == null || decisionTime == null || decisionDate == null) {
            throw new IllegalArgumentException("Review decision and time are required");
        }
        if (decision == ReviewResult.CORRECTION_REQUESTED) {
            if (feedback == null || feedback.isBlank()) {
                throw new IllegalArgumentException("Correction feedback is required");
            }
            if (pagesToRedo == null || pagesToRedo <= 0 || pagesToRedo > 10000) {
                throw new IllegalArgumentException("Correction pages must be between 1 and 10000");
            }
        }
        result = decision;
        mistakesNotes = feedback;
        correctionPages = decision == ReviewResult.CORRECTION_REQUESTED ? pagesToRedo : null;
        decidedAt = decisionTime;
        checkingDate = decisionDate;
        openSlot = null;
    }

    private void requireOpen() {
        if (!isOpen()) {
            throw new IllegalStateException("Closed reviews cannot be changed");
        }
    }
}

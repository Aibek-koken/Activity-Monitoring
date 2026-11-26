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
import java.time.Instant;

@Entity
@Table(name = "review_annotations")
public class ReviewAnnotation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private AnnotationKind kind;

    @Column(name = "rects_json", nullable = false, columnDefinition = "text")
    private String rectsJson;

    @Column(name = "quoted_text", columnDefinition = "text")
    private String quotedText;

    @Column(name = "comment", columnDefinition = "text")
    private String comment;

    @Column(name = "suggested_replacement", columnDefinition = "text")
    private String suggestedReplacement;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReviewAnnotation() {
    }

    public ReviewAnnotation(
            Review review,
            int pageNumber,
            AnnotationKind kind,
            String rectsJson,
            String quotedText,
            String comment,
            String suggestedReplacement,
            Instant now
    ) {
        this.review = review;
        this.pageNumber = pageNumber;
        this.kind = kind;
        this.rectsJson = rectsJson;
        this.quotedText = quotedText;
        this.comment = comment;
        this.suggestedReplacement = suggestedReplacement;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(
            int pageNumber,
            AnnotationKind kind,
            String rectsJson,
            String quotedText,
            String comment,
            String suggestedReplacement,
            Instant now
    ) {
        this.pageNumber = pageNumber;
        this.kind = kind;
        this.rectsJson = rectsJson;
        this.quotedText = quotedText;
        this.comment = comment;
        this.suggestedReplacement = suggestedReplacement;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Review getReview() {
        return review;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public AnnotationKind getKind() {
        return kind;
    }

    public String getRectsJson() {
        return rectsJson;
    }

    public String getQuotedText() {
        return quotedText;
    }

    public String getComment() {
        return comment;
    }

    public String getSuggestedReplacement() {
        return suggestedReplacement;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

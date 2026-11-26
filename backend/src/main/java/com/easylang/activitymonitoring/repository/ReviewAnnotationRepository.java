package com.easylang.activitymonitoring.repository;

import com.easylang.activitymonitoring.model.ReviewAnnotation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewAnnotationRepository extends JpaRepository<ReviewAnnotation, Long> {
    List<ReviewAnnotation> findByReviewIdOrderByPageNumberAscIdAsc(Long reviewId);

    Optional<ReviewAnnotation> findByIdAndReviewId(Long id, Long reviewId);

    long countByReviewId(Long reviewId);

    interface ReviewCount { Long getReviewId(); long getTotal(); }
    @Query("select mark.review.id as reviewId, count(mark) as total from ReviewAnnotation mark where mark.review.id in :reviewIds group by mark.review.id")
    List<ReviewCount> countForReviews(List<Long> reviewIds);
}

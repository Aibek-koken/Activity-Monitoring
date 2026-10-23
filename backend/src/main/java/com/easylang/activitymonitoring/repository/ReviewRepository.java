package com.easylang.activitymonitoring.repository;

import com.easylang.activitymonitoring.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsByActivityIdAndCycleNumber(Long activityId, int cycleNumber);
}

package com.easylang.activitymonitoring.repository;

import com.easylang.activitymonitoring.model.WeeklyEstimate;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyEstimateRepository extends JpaRepository<WeeklyEstimate, Long> {
    Optional<WeeklyEstimate> findByActivityIdAndWeekStartDate(Long activityId, LocalDate weekStartDate);

    List<WeeklyEstimate> findByActivityIdOrderByWeekStartDateDesc(Long activityId);

    boolean existsByActivityIdAndWeekStartDate(Long activityId, LocalDate weekStartDate);
}

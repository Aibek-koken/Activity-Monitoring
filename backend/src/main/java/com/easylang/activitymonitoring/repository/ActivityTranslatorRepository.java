package com.easylang.activitymonitoring.repository;

import com.easylang.activitymonitoring.model.ActivityTranslator;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityTranslatorRepository extends JpaRepository<ActivityTranslator, Long> {
    boolean existsByActivityIdAndTranslatorId(Long activityId, Long translatorId);

    Optional<ActivityTranslator> findByActivityIdAndTranslatorId(Long activityId, Long translatorId);
}

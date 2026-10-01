package com.easylang.activitymonitoring.repository;

import com.easylang.activitymonitoring.model.WorkRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkRecordRepository extends JpaRepository<WorkRecord, Long> {
    List<WorkRecord> findByActivityIdAndTranslatorIdOrderByRecordDateDescIdDesc(Long activityId, Long translatorId);

    Optional<WorkRecord> findByActivityIdAndTranslatorIdAndRecordDate(
            Long activityId,
            Long translatorId,
            LocalDate recordDate
    );

    Optional<WorkRecord> findTopByActivityIdAndTranslatorIdOrderByRecordDateDescIdDesc(Long activityId, Long translatorId);

    long countByActivityIdAndTranslatorId(Long activityId, Long translatorId);

    @Query("""
            select coalesce(sum(record.translatedVolumePages), 0)
            from WorkRecord record
            where record.activity.id = :activityId
              and record.translator.id = :translatorId
            """)
    BigDecimal sumTranslatedVolumePages(
            @Param("activityId") Long activityId,
            @Param("translatorId") Long translatorId
    );
}

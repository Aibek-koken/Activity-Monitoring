package com.easylang.activitymonitoring.repository;

import com.easylang.activitymonitoring.model.WorkRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkRecordRepository extends JpaRepository<WorkRecord, Long> {
    interface ActivityProgressRow {
        Long getActivityId();
        BigDecimal getTotal();
        long getRecordCount();
        LocalDate getLastRecordDate();
    }

    @Query("""
            select record.activity.id as activityId,
                   sum(record.translatedVolume) as total,
                   count(record) as recordCount,
                   max(record.recordDate) as lastRecordDate
            from WorkRecord record
            where record.translator.id = :translatorId and record.activity.id in :activityIds
            group by record.activity.id
            """)
    List<ActivityProgressRow> findProgressForActivityList(@Param("translatorId") Long translatorId,
                                                           @Param("activityIds") Collection<Long> activityIds);

    List<WorkRecord> findByActivityIdAndTranslatorIdOrderByRecordDateDescIdDesc(Long activityId, Long translatorId);

    Optional<WorkRecord> findByActivityIdAndTranslatorIdAndRecordDate(
            Long activityId,
            Long translatorId,
            LocalDate recordDate
    );

    Optional<WorkRecord> findByIdAndActivityIdAndTranslatorId(Long id, Long activityId, Long translatorId);

    Optional<WorkRecord> findTopByActivityIdAndTranslatorIdOrderByRecordDateDescIdDesc(Long activityId, Long translatorId);

    long countByActivityIdAndTranslatorId(Long activityId, Long translatorId);

    boolean existsByTranslatorIdAndRecordDate(Long translatorId, LocalDate recordDate);

    @Query("select distinct record.translator.id from WorkRecord record where record.recordDate = :recordDate")
    List<Long> findTranslatorIdsWithRecordsOn(@Param("recordDate") LocalDate recordDate);

    @Query("""
            select coalesce(sum(record.translatedVolume), 0)
            from WorkRecord record
            where record.activity.id = :activityId
              and record.translator.id = :translatorId
            """)
    BigDecimal sumTranslatedVolume(
            @Param("activityId") Long activityId,
            @Param("translatorId") Long translatorId
    );

    @Query("""
            select coalesce(sum(record.reworkPages), 0)
            from WorkRecord record
            where record.activity.id = :activityId
              and record.translator.id = :translatorId
              and record.reworkReview.id = :reviewId
            """)
    BigDecimal sumReworkPagesForReview(
            @Param("activityId") Long activityId,
            @Param("translatorId") Long translatorId,
            @Param("reviewId") Long reviewId
    );

    @Query("""
            select coalesce(sum(record.reworkPages), 0)
            from WorkRecord record
            where record.activity.id = :activityId
              and record.translator.id = :translatorId
            """)
    BigDecimal sumAllReworkPages(
            @Param("activityId") Long activityId,
            @Param("translatorId") Long translatorId
    );
}

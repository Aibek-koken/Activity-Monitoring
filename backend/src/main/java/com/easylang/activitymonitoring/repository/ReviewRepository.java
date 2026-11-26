package com.easylang.activitymonitoring.repository;

import com.easylang.activitymonitoring.model.Review;
import com.easylang.activitymonitoring.model.ReviewKind;
import com.easylang.activitymonitoring.model.User;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    interface OpenWorkload {
        long getOpenCount();

        BigDecimal getKnownExpectedHours();

        Long getMissingEstimateCount();
    }

    boolean existsByActivityIdAndCycleNumber(Long activityId, int cycleNumber);

    long countByActivityId(Long activityId);

    List<Review> findByActivityIdOrderByCycleNumberDesc(Long activityId);

    Optional<Review> findTopByActivityIdOrderByCycleNumberDesc(Long activityId);

    Optional<Review> findFirstByActivityIdAndResultIsNullOrderByCycleNumberDesc(Long activityId);

    List<Review> findByActivityIdAndKindAndWeekStartDateOrderByCycleNumberDesc(
            Long activityId,
            ReviewKind kind,
            LocalDate weekStartDate
    );

    boolean existsByActivityIdAndKindAndWeekStartDateAndDocumentSha256(
            Long activityId,
            ReviewKind kind,
            LocalDate weekStartDate,
            String documentSha256
    );

    boolean existsByActivityIdAndKindAndWeekStartDate(Long activityId, ReviewKind kind, LocalDate weekStartDate);

    @Query("""
            select review.activity.id
            from Review review
            where review.result = com.easylang.activitymonitoring.model.ReviewResult.CORRECTION_REQUESTED
              and review.cycleNumber = (
                  select max(later.cycleNumber)
                  from Review later
                  where later.activity.id = review.activity.id
              )
              and exists (
                  select 1
                  from ActivityTranslator assignment
                  where assignment.activity.id = review.activity.id
                    and assignment.translator.id = :translatorId
                    and assignment.responsible = true
              )
            """)
    List<Long> findActivityIdsWithPendingCorrection(@Param("translatorId") Long translatorId);

    @Query("""
            select distinct review.activity.id
            from Review review
            where review.kind = com.easylang.activitymonitoring.model.ReviewKind.WEEKLY
              and review.weekStartDate = :weekStartDate
            """)
    List<Long> findActivityIdsWithWeeklyVersion(@Param("weekStartDate") LocalDate weekStartDate);

    @Query("""
            select review
            from Review review
            join fetch review.activity activity
            join fetch activity.project
            join fetch review.chiefEditor
            where review.id = :reviewId
            """)
    Optional<Review> findByIdWithContext(@Param("reviewId") Long reviewId);

    @Query("""
            select review.activity.id
            from Review review
            where review.id = :reviewId and review.chiefEditor.id = :chiefEditorId
            """)
    Optional<Long> findOwnedActivityId(
            @Param("reviewId") Long reviewId,
            @Param("chiefEditorId") Long chiefEditorId
    );

    @Query("""
            select review
            from Review review
            join fetch review.activity activity
            join fetch activity.project
            where review.id = :reviewId and review.chiefEditor.id = :chiefEditorId
            """)
    Optional<Review> findOwnedById(
            @Param("reviewId") Long reviewId,
            @Param("chiefEditorId") Long chiefEditorId
    );

    @Query(
            value = """
                    select review
                    from Review review
                    join fetch review.activity activity
                    join fetch activity.project
                    where review.chiefEditor.id = :chiefEditorId
                      and review.result is null
                      and (:projectId is null or activity.project.id = :projectId)
                      and (:kind is null or review.kind = :kind)
                      and (lower(activity.activityName) like :search escape '!' or lower(activity.activityNumber) like :search escape '!')
                    order by review.submittedAt desc, review.id desc
                    """,
            countQuery = """
                    select count(review)
                    from Review review
                    join review.activity activity
                    where review.chiefEditor.id = :chiefEditorId
                      and review.result is null
                      and (:projectId is null or activity.project.id = :projectId)
                      and (:kind is null or review.kind = :kind)
                      and (lower(activity.activityName) like :search escape '!' or lower(activity.activityNumber) like :search escape '!')
                    """
    )
    Page<Review> findOpenAssignedToChiefEditor(
            @Param("chiefEditorId") Long chiefEditorId,
            @Param("projectId") Long projectId,
            @Param("kind") ReviewKind kind,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("""
            select count(review) as openCount,
                   sum(review.expectedCheckingHours) as knownExpectedHours,
                   sum(case when review.expectedCheckingHours is null then 1 else 0 end) as missingEstimateCount
            from Review review
            where review.chiefEditor.id = :chiefEditorId
              and review.result is null
            """)
    OpenWorkload summarizeOpenWorkload(@Param("chiefEditorId") Long chiefEditorId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Review review
            set review.chiefEditor = :toEditor
            where review.chiefEditor.id = :fromEditorId
              and review.activity.id in (
                  select activity.id
                  from Activity activity
                  where activity.activityNumber like :activityNumberPattern
              )
            """)
    int reassignChiefEditorForActivityNumbers(
            @Param("fromEditorId") Long fromEditorId,
            @Param("toEditor") User toEditor,
            @Param("activityNumberPattern") String activityNumberPattern
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select review from Review review where review.id = :reviewId and review.activity.id = :activityId")
    Optional<Review> findByIdAndActivityIdForUpdate(
            @Param("reviewId") Long reviewId,
            @Param("activityId") Long activityId
    );
}

package com.easylang.activitymonitoring.repository;

import com.easylang.activitymonitoring.model.ActivityTranslator;
import com.easylang.activitymonitoring.model.ActivityStatus;
import com.easylang.activitymonitoring.model.Role;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityTranslatorRepository extends JpaRepository<ActivityTranslator, Long> {
    boolean existsByActivityIdAndTranslatorId(Long activityId, Long translatorId);

    Optional<ActivityTranslator> findByActivityIdAndTranslatorId(Long activityId, Long translatorId);

    @Query("""
            select distinct assignment.translator.id
            from ActivityTranslator assignment
            where assignment.activity.status in :statuses
              and assignment.assignedDate <= :date
              and assignment.translator.active = true
              and assignment.translator.role = :role
            """)
    List<Long> findActiveTranslatorIds(@Param("statuses") Collection<ActivityStatus> statuses,
                                       @Param("role") Role role,
                                       @Param("date") LocalDate date);

    @Query("""
            select assignment
            from ActivityTranslator assignment
            join fetch assignment.activity activity
            join fetch assignment.translator translator
            where assignment.responsible = true
              and activity.status in :statuses
              and assignment.assignedDate <= :date
              and translator.active = true
              and translator.role = :role
            """)
    List<ActivityTranslator> findActiveResponsibleAssignments(@Param("statuses") Collection<ActivityStatus> statuses,
                                                               @Param("role") Role role,
                                                               @Param("date") LocalDate date);

    @Query("""
            select assignment.activity.id
            from ActivityTranslator assignment
            where assignment.translator.id = :translatorId
              and assignment.responsible = true
              and assignment.activity.status in :statuses
              and assignment.assignedDate <= :date
            """)
    List<Long> findActiveResponsibleActivityIds(@Param("translatorId") Long translatorId,
                                                @Param("statuses") Collection<ActivityStatus> statuses,
                                                @Param("date") LocalDate date);

    @Query("""
            select count(assignment) > 0
            from ActivityTranslator assignment
            where assignment.translator.id = :translatorId
              and assignment.activity.status in :statuses
              and assignment.assignedDate <= :date
            """)
    boolean hasActiveAssignment(@Param("translatorId") Long translatorId,
                                @Param("statuses") Collection<ActivityStatus> statuses,
                                @Param("date") LocalDate date);
}

package com.easylang.activitymonitoring.repository;

import com.easylang.activitymonitoring.model.Activity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityRepository extends JpaRepository<Activity, Long> {
    Optional<Activity> findByActivityNumber(String activityNumber);

    @Query("""
            select distinct activity
            from Activity activity
            join fetch activity.project project
            join ActivityTranslator assignment on assignment.activity = activity
            where assignment.translator.id = :translatorId
              and (
                :query is null
                or lower(activity.activityNumber) like lower(concat('%', :query, '%'))
                or lower(activity.activityName) like lower(concat('%', :query, '%'))
              )
            order by activity.createdDate desc, activity.activityNumber asc
            """)
    List<Activity> findAssignedToTranslator(
            @Param("translatorId") Long translatorId,
            @Param("query") String query
    );

    @Query("""
            select activity
            from Activity activity
            join fetch activity.project project
            join ActivityTranslator assignment on assignment.activity = activity
            where assignment.translator.id = :translatorId
              and activity.id = :activityId
            """)
    Optional<Activity> findAssignedToTranslatorById(
            @Param("translatorId") Long translatorId,
            @Param("activityId") Long activityId
    );
}

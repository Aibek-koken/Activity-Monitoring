package com.easylang.activitymonitoring.repository;

import com.easylang.activitymonitoring.model.Project;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    Optional<Project> findByProjectName(String projectName);

    List<Project> findByProjectNameStartingWith(String prefix);

    @Query("select distinct a.project from ActivityTranslator assignment join assignment.activity a where assignment.translator.id = :translatorId")
    List<Project> findAssignedToTranslator(@Param("translatorId") Long translatorId);
}

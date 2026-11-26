package com.easylang.activitymonitoring.repository;

import com.easylang.activitymonitoring.model.ProjectChiefEditor;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectChiefEditorRepository extends JpaRepository<ProjectChiefEditor, Long> {
    Optional<ProjectChiefEditor> findByProjectIdAndResponsibleTrue(Long projectId);

    boolean existsByProjectIdAndChiefEditorId(Long projectId, Long chiefEditorId);

    Optional<ProjectChiefEditor> findByProjectIdAndChiefEditorId(Long projectId, Long chiefEditorId);

    long countByChiefEditorIdAndResponsibleTrue(Long chiefEditorId);

    @Query("""
            select assignment
            from ProjectChiefEditor assignment
            join fetch assignment.project project
            where assignment.chiefEditor.id = :chiefEditorId
            order by lower(project.projectName), project.id
            """)
    List<ProjectChiefEditor> findAssignedProjects(@Param("chiefEditorId") Long chiefEditorId);
}

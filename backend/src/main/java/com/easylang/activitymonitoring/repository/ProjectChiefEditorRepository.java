package com.easylang.activitymonitoring.repository;

import com.easylang.activitymonitoring.model.ProjectChiefEditor;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectChiefEditorRepository extends JpaRepository<ProjectChiefEditor, Long> {
    Optional<ProjectChiefEditor> findByProjectIdAndResponsibleTrue(Long projectId);

    boolean existsByProjectIdAndChiefEditorId(Long projectId, Long chiefEditorId);
}

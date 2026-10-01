package com.easylang.activitymonitoring.repository;

import com.easylang.activitymonitoring.model.Project;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    Optional<Project> findByProjectName(String projectName);
}

package com.easylang.activitymonitoring.service;

import com.easylang.activitymonitoring.model.Project;
import com.easylang.activitymonitoring.model.ProjectChiefEditor;
import com.easylang.activitymonitoring.model.User;
import com.easylang.activitymonitoring.repository.ProjectChiefEditorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChiefEditorAssignmentService {

    public static final int MAX_RESPONSIBLE_PROJECTS = 1;

    private final ProjectChiefEditorRepository projectChiefEditorRepository;

    public ChiefEditorAssignmentService(ProjectChiefEditorRepository projectChiefEditorRepository) {
        this.projectChiefEditorRepository = projectChiefEditorRepository;
    }

    @Transactional(readOnly = true)
    public boolean canBecomeResponsible(User chiefEditor) {
        return projectChiefEditorRepository.countByChiefEditorIdAndResponsibleTrue(chiefEditor.getId())
                < MAX_RESPONSIBLE_PROJECTS;
    }

    @Transactional
    public ProjectChiefEditor assign(Project project, User chiefEditor, boolean preferredResponsible) {
        ProjectChiefEditor assignment = projectChiefEditorRepository
                .findByProjectIdAndChiefEditorId(project.getId(), chiefEditor.getId())
                .orElse(null);
        if (assignment == null) {
            boolean responsible = preferredResponsible && canBecomeResponsible(chiefEditor);
            return projectChiefEditorRepository.save(new ProjectChiefEditor(project, chiefEditor, responsible));
        }
        if (preferredResponsible && !assignment.isResponsible() && canBecomeResponsible(chiefEditor)) {
            assignment.makeResponsible();
            return projectChiefEditorRepository.saveAndFlush(assignment);
        }
        return assignment;
    }

    @Transactional
    public void move(Project project, User from, User to, boolean preferredResponsible) {
        if (!from.getId().equals(to.getId())) {
            projectChiefEditorRepository.findByProjectIdAndChiefEditorId(project.getId(), from.getId())
                    .ifPresent(assignment -> {
                        projectChiefEditorRepository.delete(assignment);
                        projectChiefEditorRepository.flush();
                    });
        }
        assign(project, to, preferredResponsible);
    }
}

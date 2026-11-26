package com.easylang.activitymonitoring.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.easylang.activitymonitoring.model.Project;
import com.easylang.activitymonitoring.model.ProjectChiefEditor;
import com.easylang.activitymonitoring.model.User;
import com.easylang.activitymonitoring.repository.ProjectChiefEditorRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChiefEditorAssignmentServiceTest {

    @Mock
    private ProjectChiefEditorRepository projectChiefEditorRepository;

    @InjectMocks
    private ChiefEditorAssignmentService service;

    @Test
    void firstResponsibleProjectIsAccepted() {
        Project project = mock(Project.class);
        User editor = mock(User.class);
        when(project.getId()).thenReturn(10L);
        when(editor.getId()).thenReturn(1L);
        when(projectChiefEditorRepository.findByProjectIdAndChiefEditorId(10L, 1L)).thenReturn(Optional.empty());
        when(projectChiefEditorRepository.countByChiefEditorIdAndResponsibleTrue(1L)).thenReturn(0L);
        when(projectChiefEditorRepository.save(any(ProjectChiefEditor.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProjectChiefEditor assignment = service.assign(project, editor, true);

        assertTrue(assignment.isResponsible());
    }

    @Test
    void additionalProjectBecomesAssistingWhenResponsibleQuotaIsFull() {
        Project project = mock(Project.class);
        User editor = mock(User.class);
        when(project.getId()).thenReturn(11L);
        when(editor.getId()).thenReturn(1L);
        when(projectChiefEditorRepository.findByProjectIdAndChiefEditorId(11L, 1L)).thenReturn(Optional.empty());
        when(projectChiefEditorRepository.countByChiefEditorIdAndResponsibleTrue(1L)).thenReturn(1L);
        when(projectChiefEditorRepository.save(any(ProjectChiefEditor.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProjectChiefEditor assignment = service.assign(project, editor, true);

        assertFalse(assignment.isResponsible());
    }
}

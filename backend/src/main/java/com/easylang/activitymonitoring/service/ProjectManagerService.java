package com.easylang.activitymonitoring.service;

import com.easylang.activitymonitoring.dto.ProjectManagerDtos.ActivityAssignmentResponse;
import com.easylang.activitymonitoring.dto.ProjectManagerDtos.AssignedTranslatorResponse;
import com.easylang.activitymonitoring.dto.ProjectManagerDtos.DashboardResponse;
import com.easylang.activitymonitoring.dto.ProjectManagerDtos.ReplaceTranslatorRequest;
import com.easylang.activitymonitoring.dto.ProjectManagerDtos.TranslatorResponse;
import com.easylang.activitymonitoring.model.ActivityStatus;
import com.easylang.activitymonitoring.model.ActivityTranslator;
import com.easylang.activitymonitoring.model.Role;
import com.easylang.activitymonitoring.model.User;
import com.easylang.activitymonitoring.repository.ActivityTranslatorRepository;
import com.easylang.activitymonitoring.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectManagerService {
    private final ActivityTranslatorRepository assignments;
    private final UserRepository users;

    public ProjectManagerService(ActivityTranslatorRepository assignments, UserRepository users) {
        this.assignments = assignments;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(Long managerId) {
        List<ActivityTranslator> managerAssignments = assignments.findForProjectManager(managerId);
        Map<Long, Integer> openCounts = managerAssignments.stream()
                .filter(a -> isOpen(a.getActivity().getStatus()))
                .collect(Collectors.groupingBy(a -> a.getTranslator().getId(), Collectors.mapping(
                        a -> a.getActivity().getId(), Collectors.collectingAndThen(Collectors.toSet(), s -> s.size()))));
        List<TranslatorResponse> translators = users.findByRoleAndActiveTrueOrderByFullNameAsc(Role.TRANSLATOR)
                .stream().map(user -> {
                    int open = openCounts.getOrDefault(user.getId(), 0);
                    return new TranslatorResponse(user.getId(), user.getFullName(), user.getInitials(), open == 0, open);
                }).toList();
        Map<Long, List<ActivityTranslator>> byActivity = managerAssignments.stream().collect(
                Collectors.groupingBy(a -> a.getActivity().getId(), LinkedHashMap::new, Collectors.toList()));
        List<ActivityAssignmentResponse> rows = byActivity.values().stream().map(activityAssignments -> {
            var activity = activityAssignments.get(0).getActivity();
            List<AssignedTranslatorResponse> assignedTranslators = activityAssignments.stream()
                    .map(a -> new AssignedTranslatorResponse(a.getTranslator().getId(),
                            a.getTranslator().getFullName(), a.isResponsible(), a.getAssignedDate()))
                    .toList();
            return new ActivityAssignmentResponse(activity.getId(), activity.getActivityNumber(),
                    activity.getActivityName(), activity.getProject().getProjectName(), activity.getStatus(),
                    assignedTranslators);
        }).toList();
        return new DashboardResponse(translators, rows);
    }

    @Transactional
    public void replaceTranslator(Long managerId, Long activityId, ReplaceTranslatorRequest request) {
        List<ActivityTranslator> activityAssignments = assignments.findAllByActivityId(activityId);
        ActivityTranslator current = activityAssignments.stream()
                .filter(ActivityTranslator::isResponsible)
                .filter(a -> a.getActivity().getProject().getProjectManager().getId().equals(managerId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activity not found"));
        User replacement = users.findById(request.translatorId())
                .filter(u -> u.getRole() == Role.TRANSLATOR && u.isActive())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Translator is unavailable"));
        if (current.getTranslator().getId().equals(replacement.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a different translator");
        }
        boolean hasConflictingWork = assignments.findForProjectManager(managerId).stream()
                .anyMatch(a -> a.getTranslator().getId().equals(replacement.getId())
                        && !a.getActivity().getId().equals(activityId)
                        && isOpen(a.getActivity().getStatus()));
        if (hasConflictingWork) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Translator has active assigned activities");
        }
        ActivityTranslator existing = assignments.findByActivityIdAndTranslatorId(activityId, replacement.getId())
                .orElse(null);
        if (existing == null) {
            assignments.deleteAll(activityAssignments.stream().filter(ActivityTranslator::isResponsible).toList());
            assignments.flush();
            assignments.save(new ActivityTranslator(current.getActivity(), replacement, true, LocalDate.now()));
            return;
        }

        activityAssignments.stream()
                .filter(ActivityTranslator::isResponsible)
                .filter(previous -> !previous.getId().equals(existing.getId()))
                .forEach(assignments::delete);
        assignments.flush();
        existing.setResponsible(true);
    }

    private boolean isOpen(ActivityStatus status) {
        return status != ActivityStatus.FINISHED && status != ActivityStatus.UNDER_CHECKING
                && status != ActivityStatus.CORRECTED && status != ActivityStatus.COMPLETED;
    }
}

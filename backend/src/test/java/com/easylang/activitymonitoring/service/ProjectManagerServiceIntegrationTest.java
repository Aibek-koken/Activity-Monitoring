package com.easylang.activitymonitoring.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.easylang.activitymonitoring.dto.ProjectManagerDtos.ReplaceTranslatorRequest;
import com.easylang.activitymonitoring.repository.ActivityRepository;
import com.easylang.activitymonitoring.repository.ActivityTranslatorRepository;
import com.easylang.activitymonitoring.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProjectManagerServiceIntegrationTest {

    @Autowired private ProjectManagerService service;
    @Autowired private ActivityRepository activities;
    @Autowired private ActivityTranslatorRepository assignments;
    @Autowired private UserRepository users;

    @Test
    void replacesTheResponsibleTranslatorWithoutCreatingTwoResponsibleAssignments() {
        var manager = users.findByEmailIgnoreCase("manager@easylang.local").orElseThrow();
        var replacement = users.findByEmailIgnoreCase("translator3@easylang.local").orElseThrow();
        var previous = users.findByEmailIgnoreCase("translator@easylang.local").orElseThrow();
        var activity = activities.findByActivityNumber("EL-2026-001").orElseThrow();

        service.replaceTranslator(manager.getId(), activity.getId(), new ReplaceTranslatorRequest(replacement.getId()));
        assignments.flush();

        var current = assignments.findByActivityIdAndResponsibleTrue(activity.getId()).orElseThrow();
        assertEquals(replacement.getId(), current.getTranslator().getId());
        assertFalse(assignments.existsByActivityIdAndTranslatorId(activity.getId(), previous.getId()));
        assertTrue(assignments.existsByActivityIdAndTranslatorId(activity.getId(), replacement.getId()));
    }

    @Test
    void rejectsReplacementWhoAlreadyHasActiveWork() {
        var manager = users.findByEmailIgnoreCase("manager@easylang.local").orElseThrow();
        var busyTranslator = users.findByEmailIgnoreCase("translator2@easylang.local").orElseThrow();
        var activity = activities.findByActivityNumber("EL-2026-001").orElseThrow();

        var error = assertThrows(ResponseStatusException.class, () ->
                service.replaceTranslator(manager.getId(), activity.getId(),
                        new ReplaceTranslatorRequest(busyTranslator.getId())));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }
}

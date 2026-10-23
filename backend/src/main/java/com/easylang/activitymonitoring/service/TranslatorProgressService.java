package com.easylang.activitymonitoring.service;

import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.ActivityDetailResponse;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.SaveWeeklyEstimateRequest;
import com.easylang.activitymonitoring.model.Activity;
import com.easylang.activitymonitoring.model.ActivityStatus;
import com.easylang.activitymonitoring.model.ActivityTranslator;
import com.easylang.activitymonitoring.model.WeeklyEstimate;
import com.easylang.activitymonitoring.repository.ActivityRepository;
import com.easylang.activitymonitoring.repository.ActivityTranslatorRepository;
import com.easylang.activitymonitoring.repository.WeeklyEstimateRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TranslatorProgressService {

    private final ActivityRepository activityRepository;
    private final ActivityTranslatorRepository assignmentRepository;
    private final WeeklyEstimateRepository estimateRepository;
    private final TranslatorActivityService activityService;
    private final Clock businessClock;

    public TranslatorProgressService(ActivityRepository activityRepository,
                                     ActivityTranslatorRepository assignmentRepository,
                                     WeeklyEstimateRepository estimateRepository,
                                     TranslatorActivityService activityService,
                                     Clock businessClock) {
        this.activityRepository = activityRepository;
        this.assignmentRepository = assignmentRepository;
        this.estimateRepository = estimateRepository;
        this.activityService = activityService;
        this.businessClock = businessClock;
    }

    @Transactional
    public ActivityDetailResponse saveCurrentEstimate(Long translatorId, Long activityId,
                                                       SaveWeeklyEstimateRequest request) {
        Activity activity = activityRepository.findByIdForUpdate(activityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activity not found"));
        ActivityTranslator assignment = assignmentRepository.findByActivityIdAndTranslatorId(activityId, translatorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activity not found"));
        if (!assignment.isResponsible()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the responsible translator can save estimates");
        }
        if (activity.getStatus() != ActivityStatus.ASSIGNED && activity.getStatus() != ActivityStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Weekly estimates are locked for this activity");
        }

        LocalDate weekStart = LocalDate.now(businessClock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        WeeklyEstimate estimate = estimateRepository.findByActivityIdAndWeekStartDate(activityId, weekStart)
                .orElseGet(() -> new WeeklyEstimate(
                        activity, assignment.getTranslator(), weekStart, request.remainingTextVolume(), businessClock.instant()
                ));
        estimate.updateRemainingTextVolume(request.remainingTextVolume(), businessClock.instant());
        estimateRepository.save(estimate);
        return activityService.getAssignedActivity(translatorId, activityId);
    }
}

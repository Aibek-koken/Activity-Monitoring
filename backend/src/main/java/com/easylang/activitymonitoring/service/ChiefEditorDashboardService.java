package com.easylang.activitymonitoring.service;

import com.easylang.activitymonitoring.dto.ChiefEditorDtos.AssignedProjectListResponse;
import com.easylang.activitymonitoring.dto.ChiefEditorDtos.AssignedProjectResponse;
import com.easylang.activitymonitoring.dto.ChiefEditorDtos.AvailabilityResponse;
import com.easylang.activitymonitoring.dto.ChiefEditorDtos.ReviewQueueItemResponse;
import com.easylang.activitymonitoring.dto.ChiefEditorDtos.ReviewQueueResponse;
import com.easylang.activitymonitoring.dto.ChiefEditorDtos.UpdateAvailabilityRequest;
import com.easylang.activitymonitoring.dto.ChiefEditorDtos.WorkloadResponse;
import com.easylang.activitymonitoring.model.ChiefEditorAvailability;
import com.easylang.activitymonitoring.model.ProjectChiefEditor;
import com.easylang.activitymonitoring.model.Review;
import com.easylang.activitymonitoring.model.ReviewKind;
import com.easylang.activitymonitoring.repository.ChiefEditorAvailabilityRepository;
import com.easylang.activitymonitoring.repository.ProjectChiefEditorRepository;
import com.easylang.activitymonitoring.repository.ReviewAnnotationRepository;
import com.easylang.activitymonitoring.repository.ReviewRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChiefEditorDashboardService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProjectChiefEditorRepository projectChiefEditorRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewAnnotationRepository annotationRepository;
    private final ChiefEditorAvailabilityRepository availabilityRepository;
    private final Clock businessClock;

    public ChiefEditorDashboardService(
            ProjectChiefEditorRepository projectChiefEditorRepository,
            ReviewRepository reviewRepository,
            ReviewAnnotationRepository annotationRepository,
            ChiefEditorAvailabilityRepository availabilityRepository,
            Clock businessClock
    ) {
        this.projectChiefEditorRepository = projectChiefEditorRepository;
        this.reviewRepository = reviewRepository;
        this.annotationRepository = annotationRepository;
        this.availabilityRepository = availabilityRepository;
        this.businessClock = businessClock;
    }

    @Transactional(readOnly = true)
    public AssignedProjectListResponse listAssignedProjects(Long chiefEditorId) {
        return new AssignedProjectListResponse(projectChiefEditorRepository.findAssignedProjects(chiefEditorId)
                .stream()
                .map(this::toAssignedProject)
                .toList());
    }

    @Transactional(readOnly = true)
    public ReviewQueueResponse listOpenReviews(
            Long chiefEditorId,
            String status,
            int page,
            int size
    ) {
        return listOpenReviews(chiefEditorId, status, page, size, null, null, null);
    }

    @Transactional(readOnly = true)
    public ReviewQueueResponse listOpenReviews(Long chiefEditorId, String status, int page, int size,
                                               Long projectId, ReviewKind kind, String query) {
        if (!"open".equalsIgnoreCase(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only the open Review queue is available");
        }
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be zero or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page size must be between 1 and 100");
        }

        Page<Review> reviews = reviewRepository.findOpenAssignedToChiefEditor(
                chiefEditorId,
                projectId,
                kind,
                "%" + (query == null ? "" : query.trim().toLowerCase()
                        .replace("!", "!!").replace("%", "!%").replace("_", "!_")) + "%",
                PageRequest.of(page, size)
        );
        List<Long> reviewIds = reviews.getContent().stream().map(Review::getId).toList();
        Map<Long, Long> markCounts = reviewIds.isEmpty() ? Map.of() : annotationRepository.countForReviews(reviewIds)
                .stream().collect(Collectors.toMap(ReviewAnnotationRepository.ReviewCount::getReviewId,
                        ReviewAnnotationRepository.ReviewCount::getTotal));
        return new ReviewQueueResponse(
                reviews.getContent().stream().map(review -> toQueueItem(review,
                        markCounts.getOrDefault(review.getId(), 0L))).toList(),
                reviews.getNumber(),
                reviews.getSize(),
                reviews.getTotalElements(),
                reviews.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public WorkloadResponse getWorkload(Long chiefEditorId) {
        ReviewRepository.OpenWorkload workload = reviewRepository.summarizeOpenWorkload(chiefEditorId);
        BigDecimal knownHours = workload.getKnownExpectedHours() == null
                ? new BigDecimal("0.00")
                : workload.getKnownExpectedHours().setScale(2, RoundingMode.HALF_UP);
        Long missingCount = workload.getMissingEstimateCount();
        ChiefEditorAvailability availability = availabilityRepository.findById(chiefEditorId).orElse(null);
        BigDecimal available = availability == null ? null : availability.getWeeklyHours();
        BigDecimal remaining = available == null ? null : available.subtract(knownHours);
        Boolean exceeds = available == null ? null : knownHours.compareTo(available) > 0;
        return new WorkloadResponse(
                workload.getOpenCount(),
                knownHours,
                missingCount == null ? 0 : missingCount,
                available,
                remaining,
                exceeds,
                missingCount == null || missingCount == 0,
                availability == null ? null : availability.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse getAvailability(Long chiefEditorId) {
        return availabilityRepository.findById(chiefEditorId)
                .map(availability -> new AvailabilityResponse(availability.getWeeklyHours(), availability.getUpdatedAt()))
                .orElse(new AvailabilityResponse(null, null));
    }

    @Transactional
    public AvailabilityResponse updateAvailability(Long chiefEditorId, UpdateAvailabilityRequest request) {
        ChiefEditorAvailability availability = availabilityRepository.findById(chiefEditorId)
                .orElseGet(() -> new ChiefEditorAvailability(chiefEditorId, request.weeklyHours(), businessClock.instant()));
        availability.updateWeeklyHours(request.weeklyHours().setScale(2, RoundingMode.HALF_UP), businessClock.instant());
        availabilityRepository.saveAndFlush(availability);
        return new AvailabilityResponse(availability.getWeeklyHours(), availability.getUpdatedAt());
    }

    private AssignedProjectResponse toAssignedProject(ProjectChiefEditor assignment) {
        return new AssignedProjectResponse(
                assignment.getProject().getId(),
                assignment.getProject().getProjectName(),
                assignment.isResponsible(),
                assignment.getProject().getCreatedDate()
        );
    }

    private ReviewQueueItemResponse toQueueItem(Review review, long annotationCount) {
        return new ReviewQueueItemResponse(
                review.getId(),
                review.getActivity().getId(),
                review.getActivity().getActivityNumber(),
                review.getActivity().getActivityName(),
                review.getActivity().getProject().getId(),
                review.getActivity().getProject().getProjectName(),
                review.getCycleNumber(),
                review.getKind(),
                review.getWeekStartDate(),
                review.getSubmittedAt(),
                review.getExpectedCheckingHours(),
                review.hasDocument(),
                annotationCount
        );
    }
}

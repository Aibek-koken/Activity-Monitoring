package com.easylang.activitymonitoring.service;

import com.easylang.activitymonitoring.dto.WorkRecordEditDtos.*;
import com.easylang.activitymonitoring.model.*;
import com.easylang.activitymonitoring.repository.*;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkRecordEditService {
    private final ActivityRepository activities;
    private final WorkRecordRepository records;
    private final WorkRecordEditRequestRepository requests;
    private final NotificationRepository notificationRepository;
    private final NotificationWriter notifications;
    private final Clock clock;

    public WorkRecordEditService(ActivityRepository activities, WorkRecordRepository records,
                                 WorkRecordEditRequestRepository requests, NotificationRepository notificationRepository,
                                 NotificationWriter notifications,
                                 Clock clock) {
        this.activities = activities;
        this.records = records;
        this.requests = requests;
        this.notificationRepository = notificationRepository;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional
    public Response request(Long translatorId, Long activityId, Long recordId, CreateRequest input) {
        Activity activity = activities.findByIdForUpdate(activityId).orElseThrow(this::notFound);
        WorkRecord record = records.findByIdAndActivityIdAndTranslatorId(recordId, activityId, translatorId)
                .orElseThrow(this::notFound);
        requireActive(activity);
        if (requests.existsByWorkRecordIdAndStatusIn(recordId,
                List.of(WorkRecordEditRequest.Status.PENDING, WorkRecordEditRequest.Status.APPROVED))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A request for this record is already open");
        }
        User manager = activity.getProject().getProjectManager();
        WorkRecordEditRequest saved = requests.saveAndFlush(new WorkRecordEditRequest(
                record, record.getTranslator(), manager, input.reason().trim(), clock.instant()));
        notifications.insertInCurrentTransaction(Notification.recordEdit(manager, activity,
                NotificationType.RECORD_EDIT_REQUESTED, saved.getId(), LocalDate.now(clock),
                "Edit requested for " + activity.getActivityNumber() + " on " + record.getRecordDate() + ".", clock.instant()));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ListResponse translatorList(Long translatorId) {
        return new ListResponse(requests.findByTranslatorIdOrderByRequestedAtDesc(translatorId)
                .stream().map(this::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public ListResponse managerList(Long managerId) {
        return new ListResponse(requests.findByProjectManagerIdOrderByRequestedAtDesc(managerId)
                .stream().map(this::toResponse).toList());
    }

    @Transactional
    public Response decide(Long managerId, Long requestId, DecideRequest input) {
        WorkRecordEditRequest request = requests.findLockedById(requestId).orElseThrow(this::notFound);
        if (!request.getProjectManager().getId().equals(managerId)) throw notFound();
        Activity activity = request.getWorkRecord().getActivity();
        if (request.getStatus() != WorkRecordEditRequest.Status.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Request already decided");
        }
        requireActive(activity);
        request.decide(input.approved(), input.comment() == null ? null : input.comment().trim(), clock.instant());
        notificationRepository.findByDedupeKey("record-edit:" + requestId + ":RECORD_EDIT_REQUESTED")
                .ifPresent(notification -> notification.markRead(clock.instant()));
        requests.saveAndFlush(request);
        notifications.insertInCurrentTransaction(Notification.recordEdit(request.getTranslator(), activity,
                input.approved() ? NotificationType.RECORD_EDIT_APPROVED : NotificationType.RECORD_EDIT_REJECTED,
                request.getId(), LocalDate.now(clock),
                input.approved() ? "Your edit request for " + activity.getActivityNumber() + " was approved."
                        : "Your edit request for " + activity.getActivityNumber() + " was declined.", clock.instant()));
        return toResponse(request);
    }

    @Transactional
    public void useApproval(Long translatorId, Long activityId, Long recordId, Long requestId) {
        if (requestId == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "An approved edit request is required");
        WorkRecordEditRequest request = requests.findLockedById(requestId).orElseThrow(this::notFound);
        if (!request.getTranslator().getId().equals(translatorId)
                || !request.getWorkRecord().getId().equals(recordId)
                || !request.getWorkRecord().getActivity().getId().equals(activityId)
                || request.getStatus() != WorkRecordEditRequest.Status.APPROVED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "An approved edit request is required");
        }
        requireActive(request.getWorkRecord().getActivity());
        request.use(clock.instant());
        notificationRepository.findByDedupeKey("record-edit:" + requestId + ":RECORD_EDIT_APPROVED")
                .ifPresent(notification -> notification.markRead(clock.instant()));
    }

    private Response toResponse(WorkRecordEditRequest request) {
        WorkRecord record = request.getWorkRecord();
        Activity activity = record.getActivity();
        return new Response(request.getId(), activity.getId(), activity.getActivityNumber(),
                activity.getProject().getProjectName(), record.getId(), record.getRecordDate(),
                request.getTranslator().getFullName(), request.getReason(), request.getStatus(),
                request.getDecisionComment(), request.getRequestedAt(), request.getDecidedAt());
    }

    private void requireActive(Activity activity) {
        if (activity.getStatus() != ActivityStatus.ASSIGNED && activity.getStatus() != ActivityStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Daily records are locked after Finish");
        }
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Edit request or record not found");
    }
}

package com.easylang.activitymonitoring.repository;

import com.easylang.activitymonitoring.model.WorkRecordEditRequest;
import com.easylang.activitymonitoring.model.WorkRecordEditRequest.Status;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface WorkRecordEditRequestRepository extends JpaRepository<WorkRecordEditRequest, Long> {
    boolean existsByWorkRecordIdAndStatusIn(Long workRecordId, List<Status> statuses);
    List<WorkRecordEditRequest> findByTranslatorIdOrderByRequestedAtDesc(Long translatorId);
    List<WorkRecordEditRequest> findByProjectManagerIdOrderByRequestedAtDesc(Long managerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from WorkRecordEditRequest request where request.id = :id")
    Optional<WorkRecordEditRequest> findLockedById(Long id);
}

package com.carenest.repository.account;

import com.carenest.entity.account.GuardianPhoneChangeRequest;
import com.carenest.utils.enums.PhoneChangeStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuardianPhoneChangeRequestRepository extends JpaRepository<GuardianPhoneChangeRequest, Long> {

    /** Khóa dòng khi duyệt/từ chối: hai người cùng duyệt thì người sau thấy đã xử lý. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from GuardianPhoneChangeRequest r where r.id = :id")
    Optional<GuardianPhoneChangeRequest> lockById(@Param("id") Long id);

    Optional<GuardianPhoneChangeRequest> findByUserIdAndStatus(Long userId, PhoneChangeStatus status);

    Optional<GuardianPhoneChangeRequest> findByGuardianIdAndStatus(Long guardianId, PhoneChangeStatus status);

    List<GuardianPhoneChangeRequest> findAllByStatusOrderByRequestedAtDescIdDesc(PhoneChangeStatus status);

    List<GuardianPhoneChangeRequest> findAllByOrderByRequestedAtDescIdDesc();

    List<GuardianPhoneChangeRequest> findAllByChildIdAndStatus(Long childId, PhoneChangeStatus status);
}

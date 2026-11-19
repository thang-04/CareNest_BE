package com.carenest.repository.account;

import com.carenest.entity.account.ChildProfileChangeRequest;
import com.carenest.utils.enums.ProfileChangeKind;
import com.carenest.utils.enums.ProfileChangeStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChildProfileChangeRequestRepository extends JpaRepository<ChildProfileChangeRequest, Long> {

    /** Khóa dòng khi duyệt/từ chối: hai người cùng duyệt thì người sau thấy đã xử lý. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ChildProfileChangeRequest r where r.id = :id")
    Optional<ChildProfileChangeRequest> lockById(@Param("id") Long id);

    Optional<ChildProfileChangeRequest> findByChildIdAndKindAndStatus(
            Long childId, ProfileChangeKind kind, ProfileChangeStatus status);

    List<ChildProfileChangeRequest> findAllByStatusOrderByRequestedAtDescIdDesc(ProfileChangeStatus status);

    List<ChildProfileChangeRequest> findAllByOrderByRequestedAtDescIdDesc();

    /** Yêu cầu mới nhất từng loại của một trẻ (GV xem trạng thái, lý do từ chối). */
    List<ChildProfileChangeRequest> findAllByChildIdOrderByRequestedAtDescIdDesc(Long childId);
}

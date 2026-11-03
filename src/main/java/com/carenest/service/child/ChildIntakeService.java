package com.carenest.service.child;

import com.carenest.dto.child.ChildGuardianContact;
import com.carenest.dto.child.ChildMedicalHistory;
import com.carenest.dto.child.ChildViewResponse;
import com.carenest.dto.child.DuplicateCheckResponse;
import com.carenest.dto.child.EnrollChildRequest;
import com.carenest.dto.child.EnrollmentDraft;
import com.carenest.dto.child.GuardianChildSummary;
import com.carenest.dto.child.GuardianRecord;
import com.carenest.dto.child.PlacementClassResponse;
import com.carenest.dto.child.ReadmitChildRequest;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.GuardianRelationship;
import com.carenest.utils.enums.HealthDeclarationAction;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tiếp nhận trẻ phía hồ sơ (CHD-01..CHD-04, CHD-10; plan 2026-10-28 Phase 2–3): tra cứu không ghi, kiểm form, ghi hồ
 * sơ, mở lại hồ sơ. Phạm vi: HT toàn trường, PHT điểm trường được phân công (AUTH-02). Lưu form kèm tạo/liên kết tài
 * khoản phụ huynh đi qua {@code ChildEnrollmentService} của feature account (feature child không gọi account).
 */
public interface ChildIntakeService {

    /** CHD-04: trùng họ tên, ngày sinh và CCCD một phụ huynh; null = không trùng. Hồ sơ ngoài phạm vi chỉ báo loại. */
    DuplicateCheckResponse checkDuplicate(
            CurrentUser actor, String fullName, String dateOfBirth, List<String> guardianIdNumbers, Long excludeId);

    /** CHD-10: lớp đang hoạt động của năm học đang hoạt động trong phạm vi, kèm số trẻ đang học. */
    List<PlacementClassResponse> listPlacementClasses(CurrentUser actor);

    /** CHD-03: trẻ đã nghỉ về Chờ xếp lớp ở điểm trường cũ, giữ mã, lịch sử, sức khỏe, tài khoản phụ huynh. */
    ChildViewResponse readmit(CurrentUser actor, Long childId, ReadmitChildRequest request);

    /**
     * Kiểm form tiếp nhận: lỗi theo ô (400), lớp/điểm trường (400 {@code classId}/{@code campusId}), trùng hồ sơ
     * (409 CHD-04). Không ghi gì.
     */
    EnrollmentDraft prepareEnrollment(CurrentUser actor, EnrollChildRequest request);

    /**
     * Ghi hồ sơ trẻ của bản đã kiểm (trẻ, hành chính, khai báo, dị ứng, lịch sử, lớp nếu có, phụ huynh); chạy trong
     * giao dịch của người gọi. {@code accountIdByPosition}: vị trí phụ huynh → tài khoản liên kết với trẻ ngay.
     */
    Long createEnrolledChild(CurrentUser actor, EnrollmentDraft draft, Map<Integer, Long> accountIdByPosition);

    /** Hồ sơ trẻ kèm phụ huynh, tài khoản, lịch sử; người gọi đã kiểm phạm vi. */
    ChildViewResponse getChildView(Long childId);

    /** Phụ huynh trên mọi hồ sơ trẻ có SĐT hoặc CCCD trong danh sách (kiểm một người một tài khoản, AUTH-18). */
    List<GuardianRecord> findGuardianRecords(Collection<String> phones, Collection<String> citizenIds);

    /** Trẻ theo id kèm trạng thái, lớp và điểm trường hiện tại. */
    List<GuardianChildSummary> findChildSummaries(Collection<Long> childIds);

    /** Phụ huynh trên hồ sơ trẻ theo thứ tự vị trí (đối chiếu người đón, ảnh phụ huynh); người gọi đã kiểm phạm vi. */
    List<ChildGuardianContact> findGuardianContacts(Long childId);

    /** Tiền sử trong khai báo sức khỏe của trẻ; rỗng khi trẻ chưa có khai báo. */
    Optional<ChildMedicalHistory> findMedicalHistory(Long childId);

    /**
     * Ghi tiền sử mới vào khai báo sức khỏe (từ phiếu khám, phiếu theo dõi; HLT-06) và một dòng lịch sử khai báo. Trẻ
     * chưa có khai báo ⇒ tạo khai báo, các mục khác "chưa cung cấp". Chạy trong giao dịch của người gọi.
     */
    void updateMedicalHistory(
            Long childId,
            ChildMedicalHistory history,
            HealthDeclarationAction action,
            String note,
            Long userId,
            Instant at);

    /** Quan hệ đã ghi cho tài khoản ở hồ sơ trẻ khác (gợi ý khi tiếp nhận anh/chị em). */
    Optional<GuardianRelationship> findRelationshipOfAccount(Long userId, String username);
}

package com.carenest.service.organization;

import com.carenest.dto.organization.CampusDetailResponse;
import com.carenest.dto.organization.CampusRequest;
import com.carenest.dto.organization.CampusResponse;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.CampusStatus;
import java.util.List;
import java.util.Optional;

/** Cấu hình trường › Điểm trường: HT xem/ghi toàn trường, PHT chỉ xem điểm trường được phân công. */
public interface CampusService {

    List<CampusResponse> getCampuses(CurrentUser currentUser, Long schoolYearId);

    CampusDetailResponse getCampusDetail(CurrentUser currentUser, Long campusId, Long schoolYearId);

    default CampusResponse createCampus(CurrentUser currentUser, CampusRequest request) {
        return createCampus(currentUser, request, null);
    }

    /** Thêm điểm trường vào năm học đang chọn (null = năm đang hoạt động); năm khác không đổi (Cần chốt 22/10/2026). */
    CampusResponse createCampus(CurrentUser currentUser, CampusRequest request, Long schoolYearId);

    default CampusResponse updateCampus(CurrentUser currentUser, Long campusId, CampusRequest request) {
        return updateCampus(currentUser, campusId, request, null);
    }

    /** Sửa thông tin điểm trường của năm học đang chọn; năm khác không đổi (Cần chốt 22/10/2026). */
    CampusResponse updateCampus(CurrentUser currentUser, Long campusId, CampusRequest request, Long schoolYearId);

    default void deleteCampus(CurrentUser currentUser, Long campusId) {
        deleteCampus(currentUser, campusId, null);
    }

    /**
     * Bỏ điểm trường khỏi năm học đang chọn; điểm trường không còn ở năm nào thì xóa hẳn (chỉ khi chưa có dữ liệu).
     */
    void deleteCampus(CurrentUser currentUser, Long campusId, Long schoolYearId);

    /** Ngừng hoạt động / hoạt động lại (Cần chốt 23/10/2026); ngừng thì kết thúc phân công PHT của điểm trường. */
    CampusResponse changeCampusStatus(CurrentUser currentUser, Long campusId, CampusStatus status);

    /** Ngừng / hoạt động lại điểm trường trong năm học đang chọn; năm khác không đổi (Cần chốt 19/11/2026). */
    CampusResponse changeCampusStatus(CurrentUser currentUser, Long campusId, CampusStatus status, Long schoolYearId);

    /** Trạng thái điểm trường cho module khác (tiếp nhận trẻ chỉ vào điểm trường đang hoạt động); không có ⇒ rỗng. */
    Optional<CampusStatus> findCampusStatus(Long campusId);
}

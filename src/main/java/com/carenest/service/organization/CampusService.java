package com.carenest.service.organization;

import com.carenest.dto.organization.CampusDetailResponse;
import com.carenest.dto.organization.CampusRequest;
import com.carenest.dto.organization.CampusResponse;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.CampusStatus;
import java.util.List;

/** Cấu hình trường › Điểm trường: HT xem/ghi toàn trường, PHT chỉ xem điểm trường được phân công. */
public interface CampusService {

    List<CampusResponse> getCampuses(CurrentUser currentUser, Long schoolYearId);

    CampusDetailResponse getCampusDetail(CurrentUser currentUser, Long campusId, Long schoolYearId);

    CampusResponse createCampus(CurrentUser currentUser, CampusRequest request);

    CampusResponse updateCampus(CurrentUser currentUser, Long campusId, CampusRequest request);

    void deleteCampus(CurrentUser currentUser, Long campusId);

    /** Ngừng hoạt động / hoạt động lại (Cần chốt 23/10/2026); ngừng thì kết thúc phân công PHT của điểm trường. */
    CampusResponse changeCampusStatus(CurrentUser currentUser, Long campusId, CampusStatus status);
}

package com.carenest.service.organization;

import com.carenest.dto.organization.CampusDetailResponse;
import com.carenest.dto.organization.CampusRequest;
import com.carenest.dto.organization.CampusResponse;
import com.carenest.security.CurrentUser;
import java.util.List;

/** Cấu hình trường › Điểm trường: HT xem/ghi toàn trường, PHT chỉ xem điểm trường được phân công. */
public interface CampusService {

    List<CampusResponse> getCampuses(CurrentUser currentUser, Long schoolYearId);

    CampusDetailResponse getCampusDetail(CurrentUser currentUser, Long campusId, Long schoolYearId);

    CampusResponse createCampus(CurrentUser currentUser, CampusRequest request);

    CampusResponse updateCampus(CurrentUser currentUser, Long campusId, CampusRequest request);

    void deleteCampus(CurrentUser currentUser, Long campusId);
}

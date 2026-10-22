package com.carenest.service.organization;

import com.carenest.dto.organization.CampusResponse;
import com.carenest.dto.organization.ClassroomResponse;
import com.carenest.security.CurrentUser;
import java.util.List;

/** Đọc cấu trúc trường cho form phân công; lọc theo phạm vi người xem (AUTH-01, AUTH-02). */
public interface SchoolStructureService {

    List<CampusResponse> listCampuses(CurrentUser actor);

    /** Lớp của năm học đang diễn ra tại campus. */
    List<ClassroomResponse> listCurrentClassrooms(CurrentUser actor, Long campusId);
}

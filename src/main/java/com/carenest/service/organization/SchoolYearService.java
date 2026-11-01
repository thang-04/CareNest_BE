package com.carenest.service.organization;

import com.carenest.dto.organization.SchoolYearDetailResponse;
import com.carenest.dto.organization.SchoolYearRequest;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.LocalDate;
import java.util.List;

/** Quản lý năm học (màn Cấu hình trường › Năm học): xem cho BGH/GV, ghi chỉ Hiệu trưởng. */
public interface SchoolYearService {

    List<SchoolYearDetailResponse> getSchoolYears(CurrentUser currentUser, SchoolYearStatus status);

    SchoolYearDetailResponse getSchoolYear(CurrentUser currentUser, Long schoolYearId);

    SchoolYearDetailResponse createSchoolYear(CurrentUser currentUser, SchoolYearRequest request);

    SchoolYearDetailResponse updateSchoolYear(CurrentUser currentUser, Long schoolYearId, SchoolYearRequest request);

    void deleteSchoolYear(CurrentUser currentUser, Long schoolYearId);

    SchoolYearDetailResponse activateSchoolYear(CurrentUser currentUser, Long schoolYearId, String confirmName);

    SchoolYearDetailResponse closeSchoolYear(CurrentUser currentUser, Long schoolYearId);

    /** CFG-YR-01: năm học đã kết thúc ⇒ 409 "School year … is closed". Gọi trước khi ghi dữ liệu gắn năm. */
    void checkYearOpen(Long schoolYearId);

    /** CFG-YR-01 cho dữ liệu theo lớp (đánh giá, xếp lớp…): kiểm năm học của lớp. */
    void checkClassYearOpen(Long classroomId);

    /** CFG-YR-01 cho dữ liệu theo ngày (điểm danh, suất ăn, thực đơn…): kiểm năm học chứa ngày đó (không có năm nào thì bỏ qua). */
    void checkDateYearOpen(LocalDate date);

    /** CFG-YR-03: năm sắp diễn ra sớm nhất (năm kế tiếp năm đang hoạt động); null nếu chưa có. */
    SchoolYearDetailResponse findNextPlannedYear();
}

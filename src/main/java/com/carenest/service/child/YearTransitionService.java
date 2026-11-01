package com.carenest.service.child;

import com.carenest.dto.child.AutoPlaceRequest;
import com.carenest.dto.child.AutoPlaceResponse;
import com.carenest.dto.child.PlacementBoardResponse;
import com.carenest.dto.child.SavePlacementsRequest;
import com.carenest.dto.child.YearReadinessResponse;
import com.carenest.dto.organization.SchoolYearDetailResponse;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;
import java.util.List;

/**
 * Chuyển năm học: xếp lớp trẻ cho năm PLANNED, kiểm tra các bước chuẩn bị, kích hoạt năm và chuyển trẻ theo bản xếp lớp
 * trong cùng transaction. Nằm ở module child vì phải ghi enrollments; đọc cấu hình năm qua service school-structure.
 */
public interface YearTransitionService {

    /**
     * ADR-0004: kích hoạt năm học xong, các trẻ này nghỉ học/ra trường từ effectiveDate (ngày bắt đầu chuyển lớp);
     * module tài khoản nghe để khóa phụ huynh (CHD-04).
     */
    record ChildrenLeftSchoolEvent(List<Long> childIds, LocalDate effectiveDate) {}

    PlacementBoardResponse getPlacements(CurrentUser currentUser, Long schoolYearId);

    int savePlacements(CurrentUser currentUser, Long schoolYearId, SavePlacementsRequest request);

    AutoPlaceResponse autoPlace(CurrentUser currentUser, Long schoolYearId, AutoPlaceRequest request);

    YearReadinessResponse getReadiness(CurrentUser currentUser, Long schoolYearId);

    SchoolYearDetailResponse activateSchoolYear(CurrentUser currentUser, Long schoolYearId, String confirmName);
}

package com.carenest.service.child;

import com.carenest.dto.child.AutoPlaceRequest;
import com.carenest.dto.child.AutoPlaceResponse;
import com.carenest.dto.child.PlacementBoardResponse;
import com.carenest.dto.child.SavePlacementsRequest;
import com.carenest.dto.child.YearReadinessResponse;
import com.carenest.dto.organization.SchoolYearDetailResponse;
import com.carenest.security.CurrentUser;

/**
 * Chuyển năm học: xếp lớp trẻ cho năm PLANNED, kiểm tra các bước chuẩn bị, kích hoạt năm và chuyển trẻ theo bản xếp lớp
 * trong cùng transaction. Nằm ở module child vì phải ghi enrollments; đọc cấu hình năm qua service school-structure.
 */
public interface YearTransitionService {

    PlacementBoardResponse getPlacements(CurrentUser currentUser, Long schoolYearId);

    int savePlacements(CurrentUser currentUser, Long schoolYearId, SavePlacementsRequest request);

    AutoPlaceResponse autoPlace(CurrentUser currentUser, Long schoolYearId, AutoPlaceRequest request);

    YearReadinessResponse getReadiness(CurrentUser currentUser, Long schoolYearId);

    SchoolYearDetailResponse activateSchoolYear(CurrentUser currentUser, Long schoolYearId, String confirmName);
}

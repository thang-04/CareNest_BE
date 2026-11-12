package com.carenest.service.meals;

import com.carenest.dto.common.ImageContent;
import com.carenest.dto.meals.InspectionDayResponse;
import com.carenest.dto.meals.InspectionReopenRequest;
import com.carenest.dto.meals.InspectionSaveRequest;
import com.carenest.dto.meals.InspectionSheetResponse;
import com.carenest.dto.meals.InspectionSignRequest;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.InspectionTab;
import java.time.LocalDate;

/**
 * Biên bản kiểm thực ba bước và soát sổ lưu mẫu (INS-01..04): đọc theo điểm trường–ngày, PHT lưu nháp, ký bằng ảnh,
 * sửa lại có lý do. Ghi, ký, sửa lại cần FOOD_SAFETY_APPROVE ở điểm trường đó.
 */
public interface InspectionService {

    /** Mọi mẫu của điểm trường–ngày: đã lưu thì trả bản lưu (đã ký trả bản chụp), chưa thì khung dựng từ dữ liệu nguồn. */
    InspectionDayResponse getDay(CurrentUser currentUser, Long campusId, LocalDate date);

    InspectionSheetResponse save(
            CurrentUser currentUser, Long campusId, LocalDate date, InspectionTab tab, InspectionSaveRequest request);

    InspectionSheetResponse sign(
            CurrentUser currentUser, Long campusId, LocalDate date, InspectionTab tab, InspectionSignRequest request);

    InspectionSheetResponse reopen(
            CurrentUser currentUser, Long campusId, LocalDate date, InspectionTab tab, InspectionReopenRequest request);

    /** Ảnh chữ ký đang hoặc đã nằm trên biên bản của điểm trường (kể cả lần ký trong lịch sử); người đọc được điểm trường xem. */
    ImageContent getSignatureImage(CurrentUser currentUser, Long campusId, String imageKey);
}

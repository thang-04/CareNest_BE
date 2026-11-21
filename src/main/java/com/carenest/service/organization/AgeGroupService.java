package com.carenest.service.organization;

import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.dto.organization.AgeGroupRequest;
import com.carenest.security.CurrentUser;
import java.util.List;

/** Cấu hình trường › Nhóm tuổi: dùng chung mọi năm học; mọi nhân sự xem, Hiệu trưởng ghi. */
public interface AgeGroupService {

    List<AgeGroupDetailResponse> getAgeGroups();

    /** Sáu nhóm tuổi chuẩn để HT chọn áp dụng cho năm học sắp diễn ra. */
    List<AgeGroupDetailResponse> getStandardAgeGroupCatalog();

    AgeGroupDetailResponse createAgeGroup(CurrentUser currentUser, AgeGroupRequest request);

    AgeGroupDetailResponse updateAgeGroup(CurrentUser currentUser, Long ageGroupId, AgeGroupRequest request);

    /** Sửa thông tin nhóm tuổi của năm học (V154): năm khác không đổi; năm đang hoạt động cập nhật cả bảng chung. */
    AgeGroupDetailResponse updateAgeGroup(
            CurrentUser currentUser, Long ageGroupId, AgeGroupRequest request, Long schoolYearId);

    void deleteAgeGroup(CurrentUser currentUser, Long ageGroupId);

    /** Nhóm tuổi của một năm học (Cần chốt 22/10/2026: mỗi năm danh sách riêng, mã nhóm giữ nguyên). */
    List<AgeGroupDetailResponse> getYearAgeGroups(Long schoolYearId);

    /** Thêm nhóm tuổi chỉ vào năm học đang chọn; trùng tên nhóm có ở năm khác ⇒ dùng lại nhóm đó. */
    AgeGroupDetailResponse createAgeGroup(CurrentUser currentUser, AgeGroupRequest request, Long schoolYearId);

    /** Bỏ nhóm tuổi khỏi năm học đang chọn; chỉ xét lớp, tổ trưởng của năm đó. */
    void deleteAgeGroup(CurrentUser currentUser, Long ageGroupId, Long schoolYearId);

    /** Năm học mới lấy danh sách nhóm tuổi của năm nguồn (hoặc năm gần nhất). */
    void copyYearAgeGroups(Long sourceYearId, Long targetYearId);
}

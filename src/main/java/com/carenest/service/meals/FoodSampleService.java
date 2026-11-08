package com.carenest.service.meals;

import com.carenest.dto.meals.FoodSampleDisposeRequest;
import com.carenest.dto.meals.FoodSampleRequest;
import com.carenest.dto.meals.FoodSampleResponse;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;
import java.util.List;

/** Lưu mẫu thức ăn (NUT-21): bếp ghi và hủy mẫu sau 24 giờ, người có FOOD_SAFETY_APPROVE duyệt theo điểm trường. */
public interface FoodSampleService {

    /** Bản lưu mẫu của điểm trường trong khoảng ngày; bếp chỉ điểm trường mình, HT/PHT mọi điểm trường. */
    List<FoodSampleResponse> getSamples(CurrentUser currentUser, Long campusId, LocalDate from, LocalDate to);

    /** Bếp ghi bản lưu mẫu cho điểm trường được phân công. */
    FoodSampleResponse createSample(CurrentUser currentUser, FoodSampleRequest request);

    /** Bếp sửa khi bản chưa duyệt và chưa ghi hủy. */
    FoodSampleResponse updateSample(CurrentUser currentUser, Long sampleId, FoodSampleRequest request);

    /** Bếp ghi hủy mẫu: từ 24 giờ sau lúc lấy mẫu. */
    FoodSampleResponse disposeSample(CurrentUser currentUser, Long sampleId, FoodSampleDisposeRequest request);

    FoodSampleResponse approveSample(CurrentUser currentUser, Long sampleId);

    FoodSampleResponse approveDisposal(CurrentUser currentUser, Long sampleId);

    /**
     * Trạng thái cho trang Bán trú hôm nay: PENDING_APPROVAL (còn lưu mẫu hoặc hủy mẫu chờ duyệt, mọi ngày),
     * UP_TO_DATE (ngày có bản ghi), NONE (ngày chưa có bản ghi); {@code pendingCount} = số việc chờ duyệt.
     */
    ApprovalStatus getApprovalStatus(Long campusId, LocalDate date);

    record ApprovalStatus(String status, int pendingCount) {}
}

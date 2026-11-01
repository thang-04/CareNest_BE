package com.carenest.service.organization;

import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.dto.organization.AgeGroupRequest;
import com.carenest.security.CurrentUser;
import java.util.List;

/** Cấu hình trường › Nhóm tuổi: dùng chung mọi năm học; mọi nhân sự xem, Hiệu trưởng ghi. */
public interface AgeGroupService {

    List<AgeGroupDetailResponse> getAgeGroups();

    AgeGroupDetailResponse createAgeGroup(CurrentUser currentUser, AgeGroupRequest request);

    AgeGroupDetailResponse updateAgeGroup(CurrentUser currentUser, Long ageGroupId, AgeGroupRequest request);

    void deleteAgeGroup(CurrentUser currentUser, Long ageGroupId);
}

package com.carenest.service.organization;

import com.carenest.dto.organization.ClassRequest;
import com.carenest.dto.organization.ClassResponse;
import com.carenest.dto.organization.CopyClassesRequest;
import com.carenest.dto.organization.CopyClassesResponse;
import com.carenest.dto.organization.SchoolStructureResponse;
import com.carenest.security.CurrentUser;

/** Cấu hình trường › Nhóm tuổi & lớp: tạo/sửa/xóa lớp, xem cấu trúc năm học, sao chép lớp từ năm khác. */
public interface ClassManagementService {

    SchoolStructureResponse getSchoolStructure(CurrentUser currentUser, Long schoolYearId, Long campusId);

    ClassResponse createClass(CurrentUser currentUser, ClassRequest request);

    ClassResponse updateClass(CurrentUser currentUser, Long classId, ClassRequest request);

    void deleteClass(CurrentUser currentUser, Long classId);

    CopyClassesResponse copyClasses(CurrentUser currentUser, Long targetYearId, CopyClassesRequest request);
}

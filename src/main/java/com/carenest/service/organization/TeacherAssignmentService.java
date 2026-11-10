package com.carenest.service.organization;

import com.carenest.dto.organization.AssignmentHistoryResponse;
import com.carenest.dto.organization.ClassTeachersRequest;
import com.carenest.dto.organization.ClassTeachersResponse;
import com.carenest.dto.organization.TeacherAssignmentResponse;
import com.carenest.dto.organization.TeamLeaderRequest;
import com.carenest.dto.organization.TeamLeaderResponse;
import com.carenest.security.CurrentUser;
import java.util.List;

/** Cấu hình trường › Phân công giáo viên (2 GV/lớp, 1 chủ nhiệm) và tổ trưởng nhóm tuổi theo năm học. */
public interface TeacherAssignmentService {

    /** campusId: id điểm trường, hoặc "all"/trống = mọi điểm trường trong phạm vi người xem. */
    TeacherAssignmentResponse getTeacherAssignments(CurrentUser currentUser, Long schoolYearId, String campusId);

    ClassTeachersResponse assignClassTeachers(CurrentUser currentUser, Long classId, ClassTeachersRequest request);

    TeamLeaderResponse getTeamLeaders(CurrentUser currentUser, Long schoolYearId);

    List<AssignmentHistoryResponse> getClassTeacherHistory(CurrentUser currentUser, Long classId);

    List<AssignmentHistoryResponse> getTeamLeaderHistory(CurrentUser currentUser, Long ageGroupId, Long schoolYearId);

    TeamLeaderResponse assignTeamLeader(CurrentUser currentUser, TeamLeaderRequest request);
}

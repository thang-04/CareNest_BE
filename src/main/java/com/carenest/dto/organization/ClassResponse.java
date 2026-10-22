package com.carenest.dto.organization;

import com.carenest.utils.enums.ClassroomStatus;
import java.util.List;

/**
 * Lớp kèm số liệu: trẻ đang học, trẻ dự kiến (năm PLANNED: đã xếp vào lớp từ năm cũ, null với năm khác), số giáo viên còn thiếu (mỗi lớp 2 GV), mức đầy lớp OPEN/NEAR/FULL (≥ 90% là NEAR).
 */
public record ClassResponse(
        Long id,
        String name,
        Long schoolYearId,
        Long campusId,
        Long ageGroupId,
        Long locationId,
        // Mã/tên phòng kèm theo để lớp năm cũ vẫn hiện phòng đã xóa (mềm) khỏi danh sách phòng
        String roomCode,
        String roomName,
        Integer capacity,
        ClassroomStatus status,
        long childCount,
        Long plannedCount,
        int missingTeachers,
        List<Long> teacherIds,
        Long homeroomTeacherId,
        boolean noRoom,
        String fill,
        // Tên giáo viên kèm theo (năm đã kết thúc vẫn hiện đúng người)
        List<ClassTeachersResponse.Teacher> teachers) {}

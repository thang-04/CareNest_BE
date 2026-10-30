package com.carenest.service.child;

import com.carenest.dto.child.ClassChildResponse;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Lớp của trẻ tại một ngày (nguồn scope cho GV/BGH); không gọi ngược policy (MODULE_MAP). */
public interface EnrollmentService {

    List<Long> findActiveClassroomIds(Long childId, LocalDate date);

    /** Trẻ đang học lớp tại ngày date, sắp theo họ tên. */
    List<ClassChildResponse> findChildrenInClassroom(Long classroomId, LocalDate date);

    /** childId → lớp đang học tại date (bản ghi mới nhất nếu nhiều); trẻ không có lớp không có trong map. */
    Map<Long, Long> findActiveClassroomIdByChildIds(Collection<Long> childIds, LocalDate date);
}

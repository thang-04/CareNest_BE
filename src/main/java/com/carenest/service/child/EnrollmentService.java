package com.carenest.service.child;

import com.carenest.dto.child.ClassChildResponse;
import java.time.LocalDate;
import java.util.List;

/** Lớp của trẻ tại một ngày (nguồn scope cho GV/BGH); không gọi ngược policy (MODULE_MAP). */
public interface EnrollmentService {

    List<Long> findActiveClassroomIds(Long childId, LocalDate date);

    /** Trẻ đang học lớp tại ngày date, sắp theo họ tên. */
    List<ClassChildResponse> findChildrenInClassroom(Long classroomId, LocalDate date);
}

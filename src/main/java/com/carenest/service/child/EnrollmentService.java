package com.carenest.service.child;

import java.time.LocalDate;
import java.util.List;

/** Lớp của trẻ tại một ngày (nguồn scope cho GV/BGH); không gọi ngược policy (MODULE_MAP). */
public interface EnrollmentService {

    List<Long> findActiveClassroomIds(Long childId, LocalDate date);
}

package com.carenest.service.child;

import com.carenest.repository.child.EnrollmentRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lớp của trẻ tại một ngày (nguồn scope cho GV/BGH); không gọi ngược policy (MODULE_MAP). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;

    public List<Long> findActiveClassroomIds(Long childId, LocalDate date) {
        return enrollmentRepository.findActiveClassroomIds(childId, date);
    }
}

package com.carenest.service.child.impl;

import com.carenest.repository.child.EnrollmentRepository;
import com.carenest.service.child.EnrollmentService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnrollmentServiceImpl implements EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;

    @Override
    public List<Long> findActiveClassroomIds(Long childId, LocalDate date) {
        return enrollmentRepository.findActiveClassroomIds(childId, date);
    }
}

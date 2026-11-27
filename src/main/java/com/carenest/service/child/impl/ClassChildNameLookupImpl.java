package com.carenest.service.child.impl;

import com.carenest.dto.child.ClassChildResponse;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.organization.ClassChildNameLookup;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClassChildNameLookupImpl implements ClassChildNameLookup {

    private final EnrollmentService enrollmentService;

    @Override
    public List<String> childNames(Long classroomId, LocalDate date) {
        return enrollmentService.findChildrenInClassroom(classroomId, date).stream()
                .map(ClassChildResponse::fullName)
                .toList();
    }
}

package com.carenest.service.organization.impl;

import com.carenest.repository.organization.ClassroomRepository;
import com.carenest.service.organization.ClassroomService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClassroomServiceImpl implements ClassroomService {

    private final ClassroomRepository classroomRepository;

    @Override
    public Optional<Long> findCampusId(Long classroomId) {
        return classroomRepository.findCampusIdById(classroomId);
    }
}

package com.carenest.service.organization;

import com.carenest.repository.organization.ClassroomRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClassroomService {

    private final ClassroomRepository classroomRepository;

    public Optional<Long> findCampusId(Long classroomId) {
        return classroomRepository.findCampusIdById(classroomId);
    }
}

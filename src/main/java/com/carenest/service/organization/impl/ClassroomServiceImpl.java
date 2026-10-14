package com.carenest.service.organization.impl;

import com.carenest.repository.organization.ClassroomRepository;
import com.carenest.service.organization.ClassroomService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClassroomServiceImpl implements ClassroomService {
    private static final String LOG_PREFIX = "[ClassroomServiceImpl]";

    private final ClassroomRepository classroomRepository;

    @Override
    public Optional<Long> findCampusId(Long classroomId) {
        String logPrefix = LOG_PREFIX + "|findCampusId|classroomId=" + classroomId;
        log.info("{}|START", logPrefix);
        try {
            Optional<Long> campusId = classroomRepository.findCampusIdById(classroomId);
            log.info("{}|END", logPrefix);
            return campusId;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}

package com.carenest.service.organization.impl;

import com.carenest.dto.organization.TeacherCandidateResponse;
import com.carenest.repository.organization.StaffAssignmentRepository;
import com.carenest.service.organization.TeacherDirectory;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// TODO(Cần): thay bằng hồ sơ nhân sự (role + điểm trường) khi nhóm chốt phương án Tài khoản nhân sự
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeacherDirectoryImpl implements TeacherDirectory {
    private static final String LOG_PREFIX = "[TeacherDirectoryImpl]";

    private final StaffAssignmentRepository staffAssignmentRepository;

    @Override
    public List<TeacherCandidateResponse> findTeachers(LocalDate date) {
        String logPrefix = LOG_PREFIX + "|findTeachers|date=" + date;
        log.info("{}|START", logPrefix);
        try {
            Map<Long, List<StaffAssignmentRepository.TeacherRow>> byUser =
                    staffAssignmentRepository.findTeacherRows().stream()
                            .collect(Collectors.groupingBy(
                                    StaffAssignmentRepository.TeacherRow::getUserId,
                                    LinkedHashMap::new,
                                    Collectors.toList()));
            // Mỗi giáo viên thuộc một điểm trường: theo lớp của phân công gần nhất (có lớp)
            List<TeacherCandidateResponse> teachers = byUser.values().stream()
                    .map(rows -> {
                        Long campusId = rows.stream()
                                .map(StaffAssignmentRepository.TeacherRow::getCampusId)
                                .filter(Objects::nonNull)
                                .findFirst()
                                .orElse(null);
                        return new TeacherCandidateResponse(
                                rows.getFirst().getUserId(),
                                rows.getFirst().getFullName(),
                                campusId,
                                campusId == null ? Set.of() : Set.of(campusId));
                    })
                    .sorted(Comparator.comparing(TeacherCandidateResponse::fullName))
                    .toList();
            log.info("{}|END", logPrefix);
            return teachers;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}

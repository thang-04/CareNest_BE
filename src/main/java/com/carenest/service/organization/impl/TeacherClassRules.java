package com.carenest.service.organization.impl;

import com.carenest.dto.organization.TeacherCandidateResponse;
import com.carenest.entity.organization.Classroom;
import com.carenest.entity.organization.SchoolYear;
import com.carenest.exception.GlobalException;
import com.carenest.repository.organization.ClassroomRepository;
import com.carenest.repository.organization.RoomRepository;
import com.carenest.repository.organization.StaffAssignmentRepository;
import com.carenest.service.organization.TeacherDirectory;
import com.carenest.utils.ApiCode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Luật phân công GV vào lớp dùng chung cho mọi đường (AUTH-24): {@code PUT /classes/{id}/teachers} và
 * {@code POST /staff-assignments}. Quyền người phân công (HT toàn trường, PHT campus mình) kiểm ở nơi gọi.
 */
@Component
@RequiredArgsConstructor
class TeacherClassRules {

    private final StaffAssignmentRepository staffAssignmentRepository;
    private final ClassroomRepository classroomRepository;
    private final RoomRepository roomRepository;
    private final TeacherDirectory teacherDirectory;

    /**
     * Năm còn mở, tài khoản còn hoạt động, GV thuộc điểm trường của lớp (GV chưa dạy lớp nào xếp được mọi nơi).
     * anyCampus: HT xếp GV vào lớp toàn trường, kể cả chuyển sang điểm trường khác (AUTH-16, user chốt 03/11/2026).
     */
    void checkTeachers(
            Classroom classroom, SchoolYear year, Collection<Long> teacherIds, LocalDate today, boolean anyCampus) {
        SchoolYearRules.checkOpen(year);
        checkAccountsActive(teacherIds);
        if (anyCampus) {
            return;
        }
        Set<Long> campusTeachers = teacherDirectory.findTeachers(today).stream()
                .filter(t -> t.campusIds().isEmpty() || t.campusIds().contains(classroom.getCampusId()))
                .map(TeacherCandidateResponse::id)
                .collect(Collectors.toSet());
        if (!campusTeachers.containsAll(teacherIds)) {
            throw GlobalException.of(ApiCode.TEACHER_WRONG_CAMPUS);
        }
    }

    void checkAccountsActive(Collection<Long> userIds) {
        if (!userIds.isEmpty()
                && !staffAssignmentRepository.findInactiveUserIds(userIds).isEmpty()) {
            throw GlobalException.of(ApiCode.TEACHER_ACCOUNT_LOCKED);
        }
    }

    /** AUTH-24: mỗi GV dạy chính một lớp trong năm học; phân công đánh dấu dạy thay ở lớp khác không tính. */
    void checkOneMainClass(Classroom classroom, SchoolYear year, Collection<Long> teacherIds, LocalDate today) {
        if (teacherIds.isEmpty()) {
            return;
        }
        Map<Long, String> otherClasses =
                classroomRepository.findAllBySchoolYearIdOrderByCampusIdAscNameAsc(classroom.getSchoolYearId()).stream()
                        .filter(c -> !c.getId().equals(classroom.getId()))
                        .collect(Collectors.toMap(Classroom::getId, Classroom::getName));
        if (otherClasses.isEmpty()) {
            return;
        }
        staffAssignmentRepository.findCurrentTeachers(otherClasses.keySet(), today).stream()
                .filter(a -> teacherIds.contains(a.getUserId()) && !a.isSubstitute())
                .findFirst()
                .ifPresent(a -> {
                    String name = roomRepository.findUserNames(Set.of(a.getUserId())).stream()
                            .map(RoomRepository.UserName::getFullName)
                            .findFirst()
                            .orElse("#" + a.getUserId());
                    throw GlobalException.of(
                            ApiCode.TEACHER_ALREADY_ASSIGNED, name, otherClasses.get(a.getClassroomId()));
                });
    }
}

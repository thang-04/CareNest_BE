package com.carenest.service.child;

import com.carenest.dto.child.ClassChildResponse;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Lớp của trẻ tại một ngày (nguồn scope cho GV/BGH); không gọi ngược policy (MODULE_MAP). */
public interface EnrollmentService {

    List<Long> findActiveClassroomIds(Long childId, LocalDate date);

    /** Điểm trường tiếp nhận của trẻ đang chờ xếp lớp; rỗng khi trẻ đã có lớp, đã nghỉ hoặc id không tồn tại. */
    Optional<Long> findPendingPlacementCampusId(Long childId);

    /** Trẻ đã nghỉ học/ra trường tại date: có lịch sử xếp lớp nhưng không còn lớp chưa kết thúc (CHD-04). */
    Set<Long> findChildIdsLeftSchool(Collection<Long> childIds, LocalDate date);

    /** Trẻ đang học lớp tại ngày date, sắp theo họ tên. */
    List<ClassChildResponse> findChildrenInClassroom(Long classroomId, LocalDate date);

    /** Trẻ có lịch sử học lớp trong kỳ; caller đã kiểm quyền lớp. */
    List<ClassChildResponse> findChildrenInClassroomDuring(Long classroomId, LocalDate from, LocalDate to);

    /** Một đoạn trẻ học lớp, đã cắt trong kỳ from..to (2 đầu tính cả). */
    record ClassStay(Long childId, LocalDate from, LocalDate to) {
        public boolean covers(LocalDate date) {
            return !date.isBefore(from) && !date.isAfter(to);
        }
    }

    /** Các đoạn học lớp trong kỳ của mọi trẻ (1 trẻ có thể nhiều đoạn); caller đã kiểm quyền lớp. */
    List<ClassStay> findStaysInClassroom(Long classroomId, LocalDate from, LocalDate to);

    /** Một đoạn học của trẻ ở một lớp, đã cắt trong kỳ from..to. */
    record ChildClassStay(Long classroomId, LocalDate from, LocalDate to) {}

    /** Các lớp trẻ đã học trong kỳ, sắp theo ngày vào lớp; caller đã kiểm quyền trẻ. */
    List<ChildClassStay> findChildStays(Long childId, LocalDate from, LocalDate to);

    /** childId → lớp đang học tại date (bản ghi mới nhất nếu nhiều); trẻ không có lớp không có trong map. */
    Map<Long, Long> findActiveClassroomIdByChildIds(Collection<Long> childIds, LocalDate date);
}

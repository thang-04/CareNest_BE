package com.carenest.repository.organization;

import com.carenest.entity.organization.AttendanceCutoff;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceCutoffRepository extends JpaRepository<AttendanceCutoff, Long> {

    List<AttendanceCutoff> findAllBySchoolYearIdOrderByIdDesc(Long schoolYearId);

    // Xóa năm học xóa cả lịch đã hủy (native để không bị lọc cancelled_at)
    @Modifying
    @Query(value = "delete from attendance_cutoffs where school_year_id = :schoolYearId", nativeQuery = true)
    void deleteAllBySchoolYearId(@Param("schoolYearId") Long schoolYearId);

    // Giờ chốt áp dụng cho một ngày: dòng có ngày áp dụng gần nhất ≤ ngày đó, cùng ngày thì lấy dòng lưu sau
    Optional<AttendanceCutoff> findFirstBySchoolYearIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(
            Long schoolYearId, LocalDate date);

    @Query(
            value = "select u.id as userId, u.full_name as fullName from user_accounts u where u.id in :ids",
            nativeQuery = true)
    List<RoomRepository.UserName> findUserNames(@Param("ids") Collection<Long> ids);
}

package com.carenest.repository.observation;

import com.carenest.entity.observation.WeeklyComment;
import com.carenest.utils.enums.WeeklyCommentStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyCommentRepository extends JpaRepository<WeeklyComment, Long> {

    List<WeeklyComment> findByClassroomIdAndChildIdInAndWeekStart(
            Long classroomId, Collection<Long> childIds, LocalDate weekStart);

    List<WeeklyComment> findByClassroomIdInAndWeekStart(Collection<Long> classroomIds, LocalDate weekStart);

    Optional<WeeklyComment> findByClassroomIdAndChildIdAndWeekStart(
            Long classroomId, Long childId, LocalDate weekStart);

    List<WeeklyComment> findByChildIdAndStatusOrderByWeekStartDesc(Long childId, WeeklyCommentStatus status);

    List<WeeklyComment> findByChildIdAndStatusAndWeekStartBetweenOrderByWeekStartAsc(
            Long childId, WeeklyCommentStatus status, LocalDate from, LocalDate to);

    /** Trẻ mà lần viết nháp sẽ không đụng tới: đã gửi (viết cả lớp), hoặc đã có chữ / đã gửi khi không viết lại. */
    @org.springframework.data.jpa.repository.Query("select c.childId from WeeklyComment c"
            + " where c.classroomId = :classroomId and c.weekStart = :weekStart and ((:overwrite = false"
            + " and (trim(c.content) <> '' or c.status = com.carenest.utils.enums.WeeklyCommentStatus.SENT))"
            + " or (:wholeClass = true and c.status = com.carenest.utils.enums.WeeklyCommentStatus.SENT))")
    java.util.List<Long> findChildIdsKeptByDrafting(
            @org.springframework.data.repository.query.Param("classroomId") Long classroomId,
            @org.springframework.data.repository.query.Param("weekStart") java.time.LocalDate weekStart,
            @org.springframework.data.repository.query.Param("overwrite") boolean overwrite,
            @org.springframework.data.repository.query.Param("wholeClass") boolean wholeClass);
}

package com.carenest.repository.observation;

import com.carenest.entity.observation.ClassDailyComment;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassDailyCommentRepository extends JpaRepository<ClassDailyComment, Long> {
    Optional<ClassDailyComment> findByClassroomIdAndDate(Long classroomId, LocalDate date);

    List<ClassDailyComment> findByClassroomIdInAndDateBetweenOrderByDateDesc(
            Collection<Long> classroomIds, LocalDate from, LocalDate to);
}

package com.carenest.repository.observation;

import com.carenest.entity.observation.ClassMonthlyComment;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassMonthlyCommentRepository extends JpaRepository<ClassMonthlyComment, Long> {

    Optional<ClassMonthlyComment> findByClassroomIdAndMonth(Long classroomId, LocalDate month);
}

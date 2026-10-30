package com.carenest.entity.observation;

import com.carenest.utils.enums.EvaluationAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Một bước xử lý đánh giá tháng / cuối năm (userId null = hệ thống tạo bản nháp). */
@Getter
@Entity
@Table(name = "child_evaluation_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChildEvaluationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evaluation_id", nullable = false)
    private Long evaluationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EvaluationAction action;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private Instant at;

    public static ChildEvaluationEvent of(Long evaluationId, EvaluationAction action, Long userId, Instant at) {
        ChildEvaluationEvent e = new ChildEvaluationEvent();
        e.evaluationId = evaluationId;
        e.action = action;
        e.userId = userId;
        e.at = at;
        return e;
    }
}

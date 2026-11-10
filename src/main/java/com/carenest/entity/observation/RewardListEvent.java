package com.carenest.entity.observation;

import com.carenest.utils.enums.RewardAction;
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

/** Một bước xử lý danh sách khen thưởng (note: ý kiến khi trả lại / phê duyệt). */
@Getter
@Entity
@Table(name = "reward_list_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RewardListEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "list_id", nullable = false)
    private Long listId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RewardAction action;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Instant at;

    @Column(length = 500)
    private String note;

    public static RewardListEvent of(Long listId, RewardAction action, Long userId, Instant at, String note) {
        RewardListEvent e = new RewardListEvent();
        e.listId = listId;
        e.action = action;
        e.userId = userId;
        e.at = at;
        e.note = note == null || note.isBlank() ? null : note;
        return e;
    }
}

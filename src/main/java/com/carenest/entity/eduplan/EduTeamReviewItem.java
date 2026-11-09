package com.carenest.entity.eduplan;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Getter
@Entity
@Table(name = "edu_team_review_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EduTeamReviewItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "team_plan_id", nullable = false)
    private Long teamPlanId;
    @Column(name = "source_plan_id", nullable = false)
    private Long sourcePlanId;
    @Column(name = "item_kind", nullable = false, length = 20)
    private String itemKind;
    @Column(name = "item_id", nullable = false)
    private Long itemId;
    @Column(nullable = false, length = 30)
    private String reason;
    @Column(nullable = false)
    private boolean resolved;
    @Column(name = "resolved_at")
    private Instant resolvedAt;
    @Column(name = "resolved_by")
    private Long resolvedBy;

    public static EduTeamReviewItem create(Long teamId,Long sourceId,String kind,Long itemId,String reason) {
        EduTeamReviewItem item=new EduTeamReviewItem();
        item.teamPlanId=teamId; item.sourcePlanId=sourceId; item.itemKind=kind; item.itemId=itemId; item.reason=reason;
        return item;
    }
    public void resolve(Long userId, Instant now) { resolved=true; resolvedBy=userId; resolvedAt=now; }
    public void invalidate(Long sourceId, String reason) { this.sourcePlanId=sourceId; this.reason=reason; resolved=false; resolvedBy=null; resolvedAt=null; }
    public void rebase(Long sourceId, boolean invalidate) {
        this.sourcePlanId = sourceId;
        if (invalidate) { resolved=false; resolvedBy=null; resolvedAt=null; }
    }
}

package com.carenest.mapper.eduplan;

import com.carenest.dto.eduplan.CurriculumRef;
import com.carenest.dto.eduplan.EduConfigResponse;
import com.carenest.dto.eduplan.PlanActorResponse;
import com.carenest.dto.eduplan.YearPlanDetailResponse;
import com.carenest.dto.eduplan.YearPlanSummaryResponse;
import com.carenest.entity.eduplan.EduBreak;
import com.carenest.entity.eduplan.EduDomain;
import com.carenest.entity.eduplan.EduGoalContent;
import com.carenest.entity.eduplan.EduGoalRow;
import com.carenest.entity.eduplan.EduPlanEvent;
import com.carenest.entity.eduplan.EduTheme;
import com.carenest.entity.eduplan.EduYearPlan;
import com.carenest.utils.enums.EduGoalRowKind;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class EduYearPlanMapper {

    public YearPlanSummaryResponse toSummaryResponse(
            EduYearPlan plan,
            String schoolYearName,
            String ageGroupName,
            long goalCount,
            long themeCount,
            Map<Long, String> userNames,
            Instant editExpiresAt,
            boolean current) {
        return new YearPlanSummaryResponse(
                plan.getId(),
                plan.getCode(),
                plan.getSchoolYearId(),
                schoolYearName,
                plan.getAgeGroupId(),
                ageGroupName,
                plan.getTitle(),
                plan.getStatus(),
                goalCount,
                themeCount,
                toOwner(plan, userNames),
                toActor(plan.getSentBy(), plan.getSentAt(), userNames),
                toActor(plan.getIssuedBy(), plan.getIssuedAt(), userNames),
                toActor(plan.getEditingBy(), plan.getEditingSince(), userNames),
                plan.getCreatedAt(),
                plan.getUpdatedAt(),
                plan.getContentRevision(),
                editExpiresAt,
                plan.getVersionNo(),
                plan.getSourcePlanId(),
                current);
    }

    public PlanActorResponse toOwner(EduYearPlan plan, Map<Long, String> userNames) {
        return new PlanActorResponse(plan.getOwnerId(), userNames.get(plan.getOwnerId()), plan.getOwnerTitle(), null);
    }

    public PlanActorResponse toActor(Long userId, Instant at, Map<Long, String> userNames) {
        return userId == null ? null : new PlanActorResponse(userId, userNames.get(userId), null, at);
    }

    public YearPlanDetailResponse.Row toRow(
            EduGoalRow row, List<YearPlanDetailResponse.Content> contents, CurriculumRef curriculum) {
        EduGoalRowKind kind = row.getKind();
        Long id = curriculum == null ? null : curriculum.id();
        boolean goal = kind == EduGoalRowKind.GOAL;
        return new YearPlanDetailResponse.Row(
                row.getId(),
                kind.code(),
                row.getText(),
                row.getGoalNo(),
                contents,
                goal ? id : null,
                goal && curriculum != null ? curriculum.text() : null,
                goal && curriculum != null ? curriculum.sectionRef() : null,
                kind == EduGoalRowKind.SUB ? id : null,
                kind == EduGoalRowKind.GROUP ? id : null,
                !goal && curriculum != null ? curriculum.text() : null);
    }

    public YearPlanDetailResponse.Content toContent(EduGoalContent content, List<Long> themeIds) {
        return new YearPlanDetailResponse.Content(content.getId(), content.getText(), themeIds);
    }

    public YearPlanDetailResponse.Theme toTheme(EduTheme theme) {
        return new YearPlanDetailResponse.Theme(
                theme.getId(),
                theme.getName(),
                theme.getAbbr(),
                theme.getStartDate(),
                theme.getEndDate(),
                theme.getFestivals(),
                theme.getNote());
    }

    public YearPlanDetailResponse.Break toBreak(EduBreak item) {
        return new YearPlanDetailResponse.Break(item.getId(), item.getLabel(), item.getStartDate(), item.getEndDate());
    }

    public YearPlanDetailResponse.History toHistory(EduPlanEvent event) {
        return new YearPlanDetailResponse.History(
                event.getAction(),
                event.getActionLabel(),
                event.getActorName(),
                event.getActorRoleLabel(),
                event.getTone(),
                event.getNote(),
                event.getOccurredAt());
    }

    public EduConfigResponse.Domain toConfigDomain(EduDomain domain) {
        return new EduConfigResponse.Domain(
                domain.getId(),
                domain.getName(),
                domain.getDisplayLabel(),
                domain.getShortLabel(),
                domain.getSortOrder(),
                domain.isStandard());
    }
}

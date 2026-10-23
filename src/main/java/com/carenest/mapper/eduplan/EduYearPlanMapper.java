package com.carenest.mapper.eduplan;

import com.carenest.dto.eduplan.EduConfigResponse;
import com.carenest.dto.eduplan.ExpectedResultResponse;
import com.carenest.dto.eduplan.PlanActorResponse;
import com.carenest.dto.eduplan.YearPlanDetailResponse;
import com.carenest.dto.eduplan.YearPlanSummaryResponse;
import com.carenest.entity.eduplan.EduBreak;
import com.carenest.entity.eduplan.EduDomain;
import com.carenest.entity.eduplan.EduExpectedResult;
import com.carenest.entity.eduplan.EduGoalContent;
import com.carenest.entity.eduplan.EduGoalRow;
import com.carenest.entity.eduplan.EduPlanEvent;
import com.carenest.entity.eduplan.EduTheme;
import com.carenest.entity.eduplan.EduYearPlan;
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
            Map<Long, String> userNames) {
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
                plan.getUpdatedAt());
    }

    public PlanActorResponse toOwner(EduYearPlan plan, Map<Long, String> userNames) {
        return new PlanActorResponse(plan.getOwnerId(), userNames.get(plan.getOwnerId()), plan.getOwnerTitle(), null);
    }

    public PlanActorResponse toActor(Long userId, Instant at, Map<Long, String> userNames) {
        return userId == null ? null : new PlanActorResponse(userId, userNames.get(userId), null, at);
    }

    public YearPlanDetailResponse.Row toRow(EduGoalRow row, List<YearPlanDetailResponse.Content> contents) {
        return new YearPlanDetailResponse.Row(
                row.getId(), row.getKind().code(), row.getText(), row.getGoalNo(), contents);
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

    public ExpectedResultResponse toExpectedResult(EduExpectedResult result) {
        return new ExpectedResultResponse(
                result.getId(), result.getDomainId(), result.getSubDomain(), result.getText());
    }
}

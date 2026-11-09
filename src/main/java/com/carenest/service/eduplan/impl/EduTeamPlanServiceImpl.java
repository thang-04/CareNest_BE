package com.carenest.service.eduplan.impl;

import com.carenest.dto.eduplan.YearPlanDetailResponse;
import com.carenest.entity.eduplan.*;
import com.carenest.exception.GlobalException;
import com.carenest.repository.eduplan.*;
import com.carenest.service.eduplan.EduTeamPlanService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EduTeamPlanServiceImpl implements EduTeamPlanService {
    private static final String LOG_PREFIX = "[EduTeamPlanServiceImpl]";
    private final EduPlanRepository plans;
    private final EduPlanSubDomainRepository subs;
    private final EduPlanExpectedResultRepository groups;
    private final EduPlanGoalRepository goals;
    private final EduGoalContentRepository contents;
    private final EduThemeRepository themes;
    private final EduBreakRepository breaks;
    private final EduContentThemeTickRepository ticks;
    private final EduPlanBranchTemplateRepository templates;
    private final EduTeamReviewItemRepository reviews;
    private final Clock clock;

    @Override
    public void synchronizeSource(EduTeamPlan target, EduPlan source) {
        String prefix=LOG_PREFIX+"|synchronizeSource|planId="+target.getId(); log.info("{}|START",prefix);
        try {
            if (Objects.equals(target.getCurriculumSourcePlanId(),source.getId())) return;
            EduPlan before=plans.findById(target.getCurriculumSourcePlanId()).orElseThrow();
            var issues=EduPlanReviewDiff.review(snapshot(before),snapshot(source),snapshot(target),today());
            syncTree(target,source);
            target.changeCurriculumSource(source.getId(),clock.instant());
            Map<String,EduTeamReviewItem> existing=reviews.findAllByTeamPlanIdOrderByIdAsc(target.getId()).stream()
                    .collect(Collectors.toMap(i->key(i.getItemKind(),i.getItemId()),Function.identity()));
            Set<String> changed=new HashSet<>();
            for(var issue:issues) {
                String key=key(issue.kind(),issue.itemId()); changed.add(key);
                var item=existing.get(key);
                if(item==null) {item=EduTeamReviewItem.create(target.getId(),source.getId(),issue.kind(),issue.itemId(),issue.reason());existing.put(key,item);}
                else item.invalidate(source.getId(),issue.reason());
            }
            for(var item:existing.values()) {
                if(!changed.contains(key(item.getItemKind(),item.getItemId()))) item.rebase(source.getId(),false);
                if(isEndedOnly(target,item)) item.resolve(target.getOwnerId(),clock.instant());
                reviews.save(item);
            }
            log.info("{}|END",prefix);
        } catch(RuntimeException ex) {log.warn("{}|ERROR|type={}",prefix,ex.getClass().getSimpleName());throw ex;}
    }

    private void syncTree(EduTeamPlan target,EduPlan source) {
        var sourceRows=rows(source.getId());
        Map<String,EduGoalRow> oldRows=rows(target.getId()).stream().collect(Collectors.toMap(r->key(r.getKind().name(),r.lineage()),Function.identity()));
        Map<Long,Long> parentIds=new HashMap<>();
        for(var row:sourceRows) {
            EduGoalRow copy=oldRows.get(key(row.getKind().name(),row.lineage()));
            if(copy==null) copy=EduGoalRow.copyFor(target.getId(),row);
            copy.initializeOwner(target);
            copy.place(row.getDomainId(),row.getSortOrder(),row.getKind(),row.getText(),row.getGoalNo());
            copy.linkCurriculum(row.getCurriculumId());
            copy.attachParents(parentIds.get(row.getSubDomainId()),parentIds.get(row.getExpectedResultId()));
            if(copy instanceof EduPlanGoal goal) goal.label(row.getDisplayLabel());
            parentIds.put(row.getId(),saveRow(copy).getId());
        }
        var oldContents=contentRows(target.getId()).stream().collect(Collectors.toMap(EduGoalContent::lineage,Function.identity()));
        for(var row:contentRows(source.getId())) {
            var copy=oldContents.get(row.lineage());
            if(copy==null) copy=EduGoalContent.copyFor(parentIds.get(row.getGoalRowId()),row);
            copy.place(parentIds.get(row.getGoalRowId()),row.getSortOrder(),row.getText()); contents.save(copy);
        }
        Map<Long,EduTheme> oldThemes=themes.findAllByYearPlanIdOrderBySortOrderAsc(target.getId()).stream()
                .collect(Collectors.toMap(EduTheme::lineage,Function.identity()));
        Map<Long,Long> themeIds=new HashMap<>();
        for(var theme:themes.findAllByYearPlanIdOrderBySortOrderAsc(source.getId())) {
            var copy=oldThemes.get(theme.lineage());
            if(copy==null)copy=EduTheme.copyFor(target.getId(),theme);
            copy.initializeOwner(target);
            if(copy.getEndDate()==null||!copy.getEndDate().isBefore(today()))
                copy.update(theme.getSortOrder(),theme.getName(),theme.getAbbr(),theme.getStartDate(),theme.getEndDate(),theme.getFestivals(),theme.getNote());
            themeIds.put(theme.getId(),themes.save(copy).getId());
        }
        // EDU-62: giữ dấu tích cũ để người soạn xử lý; không tự tick nội dung mới.
        var oldBreaks=breaks.findAllByYearPlanIdOrderBySortOrderAsc(target.getId()).stream()
                .collect(Collectors.toMap(EduBreak::lineage,Function.identity()));
        for(var b:breaks.findAllByYearPlanIdOrderBySortOrderAsc(source.getId())) {
            var copy=oldBreaks.get(b.lineage());if(copy==null)copy=EduBreak.copyFor(target.getId(),b);
            copy.initializeOwner(target);copy.update(b.getSortOrder(),b.getLabel(),b.getStartDate(),b.getEndDate());breaks.save(copy);
        }
        var oldTemplates=templates.findAllByYearPlanIdOrderBySortOrderAsc(target.getId()).stream()
                .collect(Collectors.toMap(EduPlanBranchTemplate::lineage,Function.identity()));
        for(var t:templates.findAllByYearPlanIdOrderBySortOrderAsc(source.getId())) {
            var copy=oldTemplates.get(t.lineage());
            if(copy==null)copy=EduPlanBranchTemplate.create(target.getId(),themeIds.get(t.getThemeId()),t.getId(),t.lineage(),t.getName(),t.getStartDate(),t.getEndDate(),t.getSortOrder());
            copy.initializeOwner(target);copy.update(themeIds.get(t.getThemeId()),t.getName(),t.getStartDate(),t.getEndDate(),t.getSortOrder());templates.save(copy);
        }
        contents.flush();
    }

    @Override
    public void validateMappings(EduPlan plan,boolean submitting) {
        String prefix=LOG_PREFIX+"|validateMappings|planId="+plan.getId();log.info("{}|START",prefix);
        try {
            if(plan.getPlanLevel()!=EduPlanLevel.TEAM||plan.getCurriculumSourcePlanId()==null)return;
            if(submitting&&hasInvalidReferences(plan,null))throw GlobalException.of(ApiCode.CURRICULUM_SOURCE_INVALID);
            if(submitting&&reviews.findAllByTeamPlanIdOrderByIdAsc(plan.getId()).stream()
                    .anyMatch(i->!i.isResolved()&&!isEndedOnly(plan,i)))
                throw GlobalException.of(ApiCode.YEAR_PLAN_REVISION_STALE);
            log.info("{}|END",prefix);
        }catch(RuntimeException ex){log.warn("{}|ERROR|type={}",prefix,ex.getClass().getSimpleName());throw ex;}
    }

    private boolean hasInvalidReferences(EduPlan plan,EduTeamReviewItem scope) {
        var current=plans.findById(plan.getCurriculumSourcePlanId()).orElseThrow();
        Set<Long> validContents=contentRows(current.getId()).stream().map(EduGoalContent::lineage).collect(Collectors.toSet());
        Set<Long> validThemes=themes.findAllByYearPlanIdOrderBySortOrderAsc(current.getId()).stream().map(EduTheme::lineage).collect(Collectors.toSet());
        var localContents=contentRows(plan.getId()).stream().collect(Collectors.toMap(EduGoalContent::getId,Function.identity()));
        var localThemes=themes.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId()).stream().collect(Collectors.toMap(EduTheme::getId,Function.identity()));
        if(localThemes.isEmpty())return false;
        return ticks.findAllByThemeIdIn(localThemes.keySet()).stream()
                .filter(t->scope==null || "CONTENT".equals(scope.getItemKind()) && Objects.equals(scope.getItemId(),t.getContentId())
                    || "THEME".equals(scope.getItemKind()) && Objects.equals(scope.getItemId(),t.getThemeId()))
                .anyMatch(t->{
            var theme=localThemes.get(t.getThemeId());var content=localContents.get(t.getContentId());
            return !ended(theme)&&(content==null||!validContents.contains(content.lineage())||!validThemes.contains(theme.lineage()));
        });
    }

    @Override
    public void resolve(EduPlan plan,Long itemId,Long userId) {
        String prefix=LOG_PREFIX+"|resolve|planId="+plan.getId();log.info("{}|START",prefix);
        try {
            var item=reviews.findById(itemId).filter(i->Objects.equals(i.getTeamPlanId(),plan.getId()))
                    .orElseThrow(()->GlobalException.of(ApiCode.YEAR_PLAN_NOT_FOUND));
            if(!Objects.equals(item.getSourcePlanId(),plan.getCurriculumSourcePlanId()))throw GlobalException.of(ApiCode.YEAR_PLAN_REVISION_STALE);
            if(hasInvalidReferences(plan,item))throw GlobalException.of(ApiCode.CURRICULUM_SOURCE_INVALID);
            item.resolve(userId,clock.instant());reviews.save(item);log.info("{}|END",prefix);
        }catch(RuntimeException ex){log.warn("{}|ERROR|type={}",prefix,ex.getClass().getSimpleName());throw ex;}
    }

    @Override @Transactional(readOnly=true)
    public List<YearPlanDetailResponse.ReviewItem> reviewItems(EduPlan plan) {
        return reviews.findAllByTeamPlanIdOrderByIdAsc(plan.getId()).stream().map(i->new YearPlanDetailResponse.ReviewItem(
                i.getId(),i.getItemKind(),i.getItemId(),i.getSourcePlanId(),i.getReason(),i.isResolved(),i.getResolvedBy(),i.getResolvedAt())).toList();
    }
    @Override @Transactional(readOnly=true)
    public List<YearPlanDetailResponse.NewItem> newItems(EduPlan plan) {
        if(!(plan instanceof EduTeamPlan team)||team.getPreviousSourcePlanId()==null)return List.of();
        var old=contentRows(team.getPreviousSourcePlanId()).stream().map(EduGoalContent::lineage).collect(Collectors.toSet());
        var next=contentRows(plan.getCurriculumSourcePlanId()).stream().map(EduGoalContent::lineage).collect(Collectors.toSet());
        return contentRows(plan.getId()).stream().filter(c->next.contains(c.lineage())&&!old.contains(c.lineage()))
                .map(c->new YearPlanDetailResponse.NewItem("CONTENT",c.getId())).toList();
    }
    private boolean isEndedOnly(EduPlan plan,EduTeamReviewItem item) {
        var ts=themes.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId());
        if("THEME".equals(item.getItemKind()))return ts.stream().filter(t->Objects.equals(t.getId(),item.getItemId())).allMatch(this::ended);
        if(!"CONTENT".equals(item.getItemKind())||ts.isEmpty())return false;
        var byId=ts.stream().collect(Collectors.toMap(EduTheme::getId,Function.identity()));
        var linked=ticks.findAllByThemeIdIn(byId.keySet()).stream().filter(t->Objects.equals(t.getContentId(),item.getItemId())).toList();
        return !linked.isEmpty()&&linked.stream().allMatch(t->ended(byId.get(t.getThemeId())));
    }
    private boolean ended(EduTheme theme){return theme!=null&&theme.getEndDate()!=null&&theme.getEndDate().isBefore(today());}
    private LocalDate today(){return LocalDate.ofInstant(clock.instant(),ZoneId.of("Asia/Ho_Chi_Minh"));}
    private static String key(String kind,Long id){return kind+":"+id;}
    private List<EduGoalRow> rows(Long id) {
        List<EduGoalRow> result=new ArrayList<>();result.addAll(subs.findAllByYearPlanIdOrderBySortOrderAsc(id));
        result.addAll(groups.findAllByYearPlanIdOrderBySortOrderAsc(id));result.addAll(goals.findAllByYearPlanIdOrderBySortOrderAsc(id));
        result.sort(Comparator.comparing(EduGoalRow::getDomainId).thenComparing(EduGoalRow::getSortOrder));return result;
    }
    private List<EduGoalContent> contentRows(Long id){var ids=rows(id).stream().filter(r->r.getKind()==EduGoalRowKind.GOAL).map(EduGoalRow::getId).toList();return ids.isEmpty()?List.of():contents.findAllByGoalRowIdInOrderBySortOrderAsc(ids);}
    private EduGoalRow saveRow(EduGoalRow r){return r instanceof EduPlanGoal g?goals.save(g):r instanceof EduPlanSubDomain s?subs.save(s):groups.save((EduPlanExpectedResult)r);}
    private YearPlanVersionDiff.Snapshot snapshot(EduPlan p) {
        var gs=rows(p.getId()).stream().filter(r->r.getKind()==EduGoalRowKind.GOAL).toList();
        var goalLineage=gs.stream().collect(Collectors.toMap(EduGoalRow::getId,EduGoalRow::lineage));
        var ts=themes.findAllByYearPlanIdOrderBySortOrderAsc(p.getId());
        var themeLineage=ts.stream().collect(Collectors.toMap(EduTheme::getId,EduTheme::lineage));
        var mapping=ts.isEmpty()?Map.<Long,Set<Long>>of():ticks.findAllByThemeIdIn(themeLineage.keySet()).stream().collect(Collectors.groupingBy(EduContentThemeTick::getContentId,Collectors.mapping(t->themeLineage.get(t.getThemeId()),Collectors.toSet())));
        return new YearPlanVersionDiff.Snapshot(p.getId(),p.getVersionNo(),gs.stream().map(g->new YearPlanVersionDiff.GoalItem(g.getId(),g.lineage(),g.getGoalNo(),g.getText())).toList(),
                contentRows(p.getId()).stream().map(c->new YearPlanVersionDiff.ContentItem(c.getId(),c.lineage(),goalLineage.get(c.getGoalRowId()),c.getText(),mapping.getOrDefault(c.getId(),Set.of()))).toList(),
                ts.stream().map(t->new YearPlanVersionDiff.ThemeItem(t.getId(),t.lineage(),t.getName(),t.getAbbr(),t.getStartDate(),t.getEndDate())).toList());
    }
}

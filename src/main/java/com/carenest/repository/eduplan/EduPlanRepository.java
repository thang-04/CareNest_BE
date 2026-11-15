package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.*;
import com.carenest.utils.enums.EduPlanLevel;
import com.carenest.utils.enums.EduPlanStatus;
import java.util.*;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class EduPlanRepository {
    private final EduYearPlanRepository years;
    private final EduTeamPlanRepository teams;

    private EduHeaderRepository<? extends EduPlan> forLevel(EduPlanLevel level) {
        return level == EduPlanLevel.SCHOOL_YEAR ? years : teams;
    }

    private List<EduPlan> merge(Collection<? extends EduPlan> a, Collection<? extends EduPlan> b) {
        return Stream.concat(a.stream(), b.stream())
                .filter(p -> !p.isDisabled())
                .sorted(Comparator.comparing(EduPlan::getId))
                .map(p -> (EduPlan) p)
                .toList();
    }

    public Optional<EduPlan> findById(Long id) {
        return years.findById(id)
                .filter(p -> !p.isDisabled())
                .map(p -> (EduPlan) p)
                .or(() -> teams.findById(id).filter(p -> !p.isDisabled()).map(p -> (EduPlan) p));
    }

    public boolean existsById(Long id) {
        return findById(id).isPresent();
    }

    public List<EduPlan> findAll() {
        return merge(years.findAll(), teams.findAll());
    }

    public List<EduPlan> findAllById(Iterable<Long> ids) {
        return merge(years.findAllById(ids), teams.findAllById(ids));
    }

    public List<EduPlan> findAllBySchoolYearIdIn(Collection<Long> ids) {
        return merge(years.findAllBySchoolYearIdIn(ids), teams.findAllBySchoolYearIdIn(ids));
    }

    public boolean existsByPlanLevelAndSchoolYearIdAndAgeGroupId(EduPlanLevel level, Long year, Long age) {
        return forLevel(level).existsBySchoolYearIdAndAgeGroupIdAndDisabledFalse(year, age);
    }

    public boolean existsBySchoolYearIdAndAgeGroupId(Long year, Long age) {
        return teams.existsBySchoolYearIdAndAgeGroupId(year, age);
    }

    public List<EduPlan> findAllByPlanLevelAndSchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(
            EduPlanLevel level, Long year, Long age) {
        return forLevel(level).findAllBySchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(year, age).stream()
                .filter(p -> !p.isDisabled())
                .map(p -> (EduPlan) p)
                .toList();
    }

    public List<EduPlan> findAllBySchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(Long year, Long age) {
        return findAllByPlanLevelAndSchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(EduPlanLevel.TEAM, year, age);
    }

    public Optional<EduPlan> findByOwnerIdAndCreatedRequestId(Long owner, UUID request) {
        return years.findByOwnerIdAndCreatedRequestId(owner, request)
                .map(p -> (EduPlan) p)
                .or(() -> teams.findByOwnerIdAndCreatedRequestId(owner, request)
                        .filter(p -> !p.isDisabled())
                        .map(p -> (EduPlan) p));
    }

    public boolean existsByCode(String code) {
        return years.existsByCode(code) || teams.existsByCode(code);
    }

    public Optional<EduPlan> findForUpdateById(Long id) {
        return years.findForUpdateById(id)
                .filter(p -> !p.isDisabled())
                .map(p -> (EduPlan) p)
                .or(() ->
                        teams.findForUpdateById(id).filter(p -> !p.isDisabled()).map(p -> (EduPlan) p));
    }

    public List<EduPlan> findAllByIdInForUpdateOrderByIdAsc(Collection<Long> ids) {
        List<EduPlan> result = new ArrayList<>();
        ids.stream().sorted().forEach(id -> findForUpdateById(id).ifPresent(result::add));
        return result;
    }

    public List<EduPlan> findAllByStatusInForUpdateOrderByIdAsc(Collection<EduPlanStatus> statuses) {
        return merge(
                years.findAllByStatusInForUpdateOrderByIdAsc(statuses),
                teams.findAllByStatusInForUpdateOrderByIdAsc(statuses));
    }

    public EduPlan save(EduPlan p) {
        return p instanceof EduYearPlan y ? years.save(y) : teams.save((EduTeamPlan) p);
    }

    public EduPlan saveAndFlush(EduPlan p) {
        EduPlan result = save(p);
        flush();
        return result;
    }

    public void flush() {
        years.flush();
        teams.flush();
    }

    public void delete(EduPlan p) {
        if (p instanceof EduYearPlan y) years.delete(y);
        else teams.delete((EduTeamPlan) p);
    }

    public Page<EduPlan> search(
            Long year,
            Long age,
            Collection<EduPlanStatus> statuses,
            Collection<Long> full,
            Collection<Long> approved,
            String keyword,
            Pageable pageable) {
        return search(EduPlanLevel.TEAM, year, age, statuses, full, approved, List.of(), keyword, pageable);
    }

    public Page<EduPlan> search(
            EduPlanLevel level,
            Long year,
            Long age,
            Collection<EduPlanStatus> statuses,
            Collection<Long> full,
            Collection<Long> approved,
            String keyword,
            Pageable pageable) {
        return search(level, year, age, statuses, full, approved, List.of(), keyword, pageable);
    }

    public Page<EduPlan> search(
            EduPlanLevel level,
            Long year,
            Long age,
            Collection<EduPlanStatus> statuses,
            Collection<Long> full,
            Collection<Long> approved,
            Collection<Long> review,
            String keyword,
            Pageable pageable) {
        return forLevel(level)
                .search(year, age, statuses, full, approved, review, keyword, pageable)
                .map(p -> (EduPlan) p);
    }
}

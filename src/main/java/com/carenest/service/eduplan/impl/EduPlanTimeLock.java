package com.carenest.service.eduplan.impl;

import com.carenest.dto.organization.SchoolYearResponse;
import com.carenest.entity.eduplan.EduPlan;
import com.carenest.entity.eduplan.EduPlanEvent;
import com.carenest.repository.eduplan.EduPlanEventRepository;
import com.carenest.repository.eduplan.EduPlanRepository;
import com.carenest.service.organization.SchoolStructureService;
import com.carenest.utils.enums.EduPlanStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Spec 004 (Q3b, Q5, Q6): kế hoạch năm học / tổ phải ban hành trước ngày bắt đầu năm học; từ ngày đó khóa gửi, tạo phiên
 * bản, cập nhật tổ — tới khi Hiệu trưởng mở khóa (sự kiện {@code unlock} kèm hạn mới).
 */
@Component
class EduPlanTimeLock {
    static final String ACTION_UNLOCK = "unlock";

    private final SchoolStructureService schoolStructureService;
    private final EduPlanEventRepository eventRepository;
    private final EduPlanRepository planRepository;
    private final Clock clock;
    private final boolean enabled;

    // ponytail: cờ chỉ để bộ test cũ (dựng kế hoạch sau ngày bắt đầu năm học) tắt khóa; ứng dụng luôn bật
    EduPlanTimeLock(
            SchoolStructureService schoolStructureService,
            EduPlanEventRepository eventRepository,
            EduPlanRepository planRepository,
            Clock clock,
            @Value("${carenest.eduplan.plan-time-lock-enabled:true}") boolean enabled) {
        this.schoolStructureService = schoolStructureService;
        this.eventRepository = eventRepository;
        this.planRepository = planRepository;
        this.clock = clock;
        this.enabled = enabled;
    }

    EduDeadline.Info info(EduPlan plan) {
        LocalDate today = LocalDate.now(clock);
        LocalDate start = schoolStructureService.findSchoolYear(plan.getSchoolYearId())
                .map(SchoolYearResponse::startDate)
                .orElse(null);
        // Mở khóa áp cho cả chuỗi phiên bản (cùng loại, năm học, nhóm tuổi): HT mở trên bản hiện hành thì phiên bản điều
        // chỉnh tạo sau đó gửi được tới hạn mới; lấy lần mở khóa mới nhất
        Set<Long> chain = planRepository
                .findAllByPlanLevelAndSchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(
                        plan.getPlanLevel(), plan.getSchoolYearId(), plan.getAgeGroupId())
                .stream()
                .map(EduPlan::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        chain.add(plan.getId());
        LocalDate unlockedTo = chain.stream()
                .filter(Objects::nonNull)
                .flatMap(id -> eventRepository.findAllByYearPlanIdOrderByOccurredAtAscIdAsc(id).stream())
                .filter(e -> ACTION_UNLOCK.equals(e.getAction()) && e.getDeadline() != null)
                .max(Comparator.comparing(EduPlanEvent::getOccurredAt))
                .map(EduPlanEvent::getDeadline)
                .orElse(null);
        LocalDate issued = plan.getIssuedAt() == null ? null : LocalDate.ofInstant(plan.getIssuedAt(), clock.getZone());
        return EduDeadline.of(start, unlockedTo, today, plan.publicStatus() == EduPlanStatus.APPROVED, issued, null);
    }

    boolean locked(EduPlan plan) {
        if (!enabled) return false;
        LocalDate effective = info(plan).effective();
        return effective != null && !LocalDate.now(clock).isBefore(effective);
    }
}

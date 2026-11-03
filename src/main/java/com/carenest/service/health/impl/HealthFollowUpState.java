package com.carenest.service.health.impl;

import com.carenest.entity.health.HealthFollowUp;
import com.carenest.entity.health.HealthFollowUpEvent;
import com.carenest.utils.enums.HealthFollowUpAction;
import com.carenest.utils.enums.HealthFollowUpEffect;
import com.carenest.utils.enums.HealthFollowUpStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Trạng thái phiếu theo dõi tính khi đọc từ dòng thời gian (thứ tự id), không lưu, không có job nhắc (user chốt
 * 2026-10-31 "chỉ hiện quá hạn"). Vòng trao đổi mở bởi lần khám mở phiếu, hoặc lần khám có bất thường mới sau khi vòng
 * trước đã trao đổi; hạn trao đổi 7 ngày tính từ lúc lưu kết quả mở vòng.
 */
final class HealthFollowUpState {

    static final int CONTACT_DUE_DAYS = 7;

    /** Vòng trao đổi hiện tại; {@code overdueDays} > 0 khi đang chờ trao đổi và đã qua hạn. */
    record Round(
            Instant startedAt,
            LocalDate contactFrom,
            LocalDate dueOn,
            boolean contacted,
            LocalDate firstContactOn,
            boolean external,
            int overdueDays) {}

    /** {@code round} null khi phiếu đã đóng; {@code roundStartIds}: id các sự kiện mở vòng. */
    record State(HealthFollowUpStatus status, Round round, Set<Long> roundStartIds) {}

    private HealthFollowUpState() {}

    static State of(HealthFollowUp f, List<HealthFollowUpEvent> events, LocalDate today, ZoneId zone) {
        HealthFollowUpEvent start = null;
        boolean contacted = false;
        LocalDate firstContactOn = null;
        boolean external = false;
        Set<Long> starts = new HashSet<>();
        for (HealthFollowUpEvent e : events) {
            // Kết quả mới khi vòng chưa trao đổi thì gộp vào vòng đó, không lùi hạn; lần khám không có bất thường mới
            // (newFinding false) chỉ vào dòng thời gian
            if (e.getAction() == HealthFollowUpAction.OPENED
                    || (e.getAction() == HealthFollowUpAction.MEASUREMENT_ADDED
                            && !Boolean.FALSE.equals(e.getNewFinding())
                            && (start == null || contacted))) {
                start = e;
                contacted = false;
                firstContactOn = null;
                external = false;
                starts.add(e.getId());
            } else if (e.getAction() == HealthFollowUpAction.PARENT_CONTACTED) {
                if (!contacted) {
                    firstContactOn = e.getEventDate() != null ? e.getEventDate() : localDate(e.getCreatedAt(), zone);
                }
                contacted = true;
            } else if (e.getAction() == HealthFollowUpAction.EXTERNAL_RESULT && contacted) {
                external = true;
            }
        }
        if (!f.isOpen()) {
            return new State(HealthFollowUpStatus.CLOSED, null, starts);
        }
        Instant startedAt = start != null ? start.getCreatedAt() : f.getOpenedAt();
        LocalDate dueOn = localDate(startedAt, zone).plusDays(CONTACT_DUE_DAYS);
        HealthFollowUpStatus status = external
                ? HealthFollowUpStatus.EXTERNAL_RESULT_RECORDED
                : contacted ? HealthFollowUpStatus.PARENT_CONTACTED : HealthFollowUpStatus.WAITING_PARENT_CONTACT;
        // Ngày trao đổi không trước ngày khám của kết quả mở vòng
        LocalDate contactFrom =
                start != null && start.getEventDate() != null ? start.getEventDate() : localDate(startedAt, zone);
        int overdueDays = status == HealthFollowUpStatus.WAITING_PARENT_CONTACT && today.isAfter(dueOn)
                ? (int) ChronoUnit.DAYS.between(dueOn, today)
                : 0;
        return new State(
                status,
                new Round(startedAt, contactFrom, dueOn, contacted, firstContactOn, external, overdueDays),
                starts);
    }

    /** Tác động của lần khám (sự kiện vào phiếu {@code entry}) lên phiếu; null khi lần khám không thuộc phiếu nào. */
    static HealthFollowUpEffect effect(HealthFollowUpEvent entry, State state) {
        if (entry == null) {
            return null;
        }
        if (entry.getAction() == HealthFollowUpAction.OPENED) {
            return HealthFollowUpEffect.OPENED;
        }
        if (Boolean.FALSE.equals(entry.getNewFinding())) {
            return HealthFollowUpEffect.NOTED;
        }
        return state.roundStartIds().contains(entry.getId())
                ? HealthFollowUpEffect.NEW_ROUND
                : HealthFollowUpEffect.MERGED;
    }

    /** Kết quả chuyên khoa gần nhất theo ngày khám, cùng ngày lấy cái ghi sau; null khi chưa có. */
    static HealthFollowUpEvent latestExternal(List<HealthFollowUpEvent> events) {
        return events.stream()
                .filter(e -> e.getAction() == HealthFollowUpAction.EXTERNAL_RESULT)
                .max(Comparator.comparing(HealthFollowUpEvent::getEventDate).thenComparing(HealthFollowUpEvent::getId))
                .orElse(null);
    }

    /** Ngày khám mở phiếu (sự kiện OPENED), không có thì ngày mở phiếu. */
    static LocalDate openedOn(HealthFollowUp f, List<HealthFollowUpEvent> events, ZoneId zone) {
        return events.stream()
                .filter(e -> e.getAction() == HealthFollowUpAction.OPENED && e.getEventDate() != null)
                .map(HealthFollowUpEvent::getEventDate)
                .findFirst()
                .orElse(localDate(f.getOpenedAt(), zone));
    }

    static LocalDate localDate(Instant at, ZoneId zone) {
        return LocalDate.ofInstant(at, zone);
    }
}

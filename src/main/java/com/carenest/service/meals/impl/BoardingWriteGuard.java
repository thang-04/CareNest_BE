package com.carenest.service.meals.impl;

import com.carenest.exception.GlobalException;
import com.carenest.repository.meals.InspectionSheetRepository;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.InspectionStatus;
import com.carenest.utils.enums.InspectionTab;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Cổng chung chặn ghi dữ liệu bán trú đã chốt (NUT-12, INS-03): ngày khác hôm nay, hoặc biên bản kiểm thực của ngày
 * đã ký. Ký biên bản đọc nguồn dưới khóa dòng {@code campuses}, nên mọi luồng ghi nguồn phải {@link #lock} trước rồi
 * mới kiểm {@link #signed}, nếu không có thể ghi lọt giữa lúc người khác đang ký.
 */
@Component
@RequiredArgsConstructor
class BoardingWriteGuard {

    /** Lý do một (điểm trường, ngày) chỉ còn xem. */
    enum LockState {
        NOT_TODAY,
        SHEET_SIGNED
    }

    private final InspectionSheetRepository sheetRepository;
    private final Clock clock;

    /** Khóa dòng điểm trường tới hết transaction: cùng khóa với ký, sửa lại biên bản. */
    void lock(Long campusId) {
        sheetRepository.lockCampus(campusId);
    }

    /** Một trong các biên bản đã ký. */
    boolean signed(Long campusId, LocalDate date, InspectionTab... tabs) {
        for (InspectionTab tab : tabs) {
            if (sheetRepository.existsByCampusIdAndSheetDateAndTabAndStatus(
                    campusId, date, tab, InspectionStatus.SIGNED)) {
                return true;
            }
        }
        return false;
    }

    /** Null ⇒ còn ghi được. Ngày khác hôm nay xét trước vì ngày cũ không ghi lùi dù biên bản chưa ký. */
    LockState stateOf(Long campusId, LocalDate date, InspectionTab... tabs) {
        if (!LocalDate.now(clock).equals(date)) {
            return LockState.NOT_TODAY;
        }
        return signed(campusId, date, tabs) ? LockState.SHEET_SIGNED : null;
    }

    /** Ném {@code code} (câu lỗi nhận ngày {0}) khi một trong các biên bản của ngày đã ký. */
    void requireUnsigned(Long campusId, LocalDate date, ApiCode code, InspectionTab... tabs) {
        if (signed(campusId, date, tabs)) {
            throw GlobalException.of(code, date);
        }
    }
}

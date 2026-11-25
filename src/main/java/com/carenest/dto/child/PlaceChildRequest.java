package com.carenest.dto.child;

import java.time.LocalDate;

/**
 * Xếp / chuyển lớp ({@code POST /children/{id}/placement}, plan tiếp nhận mục 4.4): {@code classId} bắt buộc;
 * {@code reason} bắt buộc khi trẻ đang có lớp (chuyển lớp), tối đa 300 ký tự. {@code effectiveDate} (G94-180/CHD-14):
 * ngày vào lớp mới, là ngày học, tới cuối năm học; trống = hôm nay (chuyển lớp) hoặc max(hôm nay, ngày bắt đầu đi học)
 * (xếp lần đầu). Ngày tương lai = lịch chuyển. Lỗi theo ô {@code classId}, {@code reason}, {@code effectiveDate}.
 */
public record PlaceChildRequest(Long classId, String reason, LocalDate effectiveDate) {

    public PlaceChildRequest(Long classId, String reason) {
        this(classId, reason, null);
    }
}

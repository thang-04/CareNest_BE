package com.carenest.dto.announcement;

import com.carenest.utils.enums.AnnouncementStatus;
import java.util.List;

/** allMatching chọn toàn bộ filter mọi trang; excludedIds là các dòng đã bỏ tick. Nếu false, chỉ xóa ids đã tick. */
public record AnnouncementSelectionRequest(
        boolean allMatching, List<Long> ids, List<Long> excludedIds, String q, AnnouncementStatus status) {}

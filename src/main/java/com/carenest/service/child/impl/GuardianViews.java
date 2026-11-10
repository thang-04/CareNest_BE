package com.carenest.service.child.impl;

import com.carenest.dto.child.ChildGuardianResponse;
import com.carenest.entity.child.ChildGuardian;
import com.carenest.repository.child.ChildGuardianRepository;
import com.carenest.utils.enums.GuardianAccountStatus;
import com.carenest.utils.enums.SmsStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Phụ huynh trên hồ sơ trẻ kèm trạng thái tài khoản, lần gửi SMS gần nhất (AUTH-20): dùng cho hồ sơ một trẻ và danh sách
 * trẻ (đọc theo lô, hai truy vấn cho cả trang). Chưa liên kết tài khoản với trẻ ⇒ Chưa kích hoạt.
 */
@Component
@RequiredArgsConstructor
class GuardianViews {
    private final ChildGuardianRepository childGuardianRepository;

    List<ChildGuardianResponse> of(Long childId) {
        return of(List.of(childId)).getOrDefault(childId, List.of());
    }

    /** Theo trẻ; trong mỗi trẻ theo vị trí trên hồ sơ. Trẻ không có phụ huynh không có khóa. */
    Map<Long, List<ChildGuardianResponse>> of(Collection<Long> childIds) {
        if (childIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ChildGuardianRepository.GuardianAccountRow> accounts =
                childGuardianRepository.findAccountRows(childIds).stream()
                        .collect(Collectors.toMap(
                                ChildGuardianRepository.GuardianAccountRow::getGuardianId, Function.identity()));
        Map<Long, List<ChildGuardianResponse>> views = new LinkedHashMap<>();
        childGuardianRepository
                .findAllByChildIdInOrderByChildIdAscPositionAsc(childIds)
                .forEach(g -> views.computeIfAbsent(g.getChildId(), id -> new ArrayList<>())
                        .add(view(g, accounts.get(g.getId()))));
        return views;
    }

    private static ChildGuardianResponse view(ChildGuardian g, ChildGuardianRepository.GuardianAccountRow row) {
        String accountStatus = row == null ? null : row.getAccountStatus();
        String smsStatus = row == null ? null : row.getSmsStatus();
        Long smsAt = row == null ? null : row.getSmsAtMillis();
        return new ChildGuardianResponse(
                g.getFullName(),
                g.getRelationship(),
                g.getPhone(),
                g.getCitizenId(),
                g.getEmail(),
                accountStatus == null
                        ? GuardianAccountStatus.NOT_ACTIVATED
                        : GuardianAccountStatus.valueOf(accountStatus),
                smsStatus == null ? null : SmsStatus.valueOf(smsStatus),
                smsAt == null ? null : Instant.ofEpochMilli(smsAt),
                g.getPhotoKey() == null ? null : "/children/" + g.getChildId() + "/guardian-photos/" + g.getId(),
                g.getId(),
                g.isAccountRecipient());
    }
}

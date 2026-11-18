package com.carenest.dto.organization;

import com.carenest.utils.enums.CampusStatus;
import java.util.List;

/**
 * Điểm trường kèm số liệu của năm học đang xem (lớp, trẻ đang học, Phó hiệu trưởng phụ trách). deleteBlockedReason:
 * lý do chưa xóa được (điểm trường chính / còn lớp, phòng, trẻ, nhân sự); null = xóa được.
 */
public record CampusResponse(
        Long id,
        String code,
        String name,
        String shortName,
        String address,
        String phone,
        boolean isMain,
        CampusStatus status,
        long classCount,
        long childCount,
        List<StaffBriefResponse> vicePrincipals,
        String deleteBlockedReason) {

    public CampusResponse(
            Long id,
            String code,
            String name,
            String shortName,
            String address,
            String phone,
            boolean isMain,
            CampusStatus status,
            long classCount,
            long childCount,
            List<StaffBriefResponse> vicePrincipals) {
        this(id, code, name, shortName, address, phone, isMain, status, classCount, childCount, vicePrincipals, null);
    }

    public CampusResponse withDeleteBlockedReason(String reason) {
        return new CampusResponse(
                id,
                code,
                name,
                shortName,
                address,
                phone,
                isMain,
                status,
                classCount,
                childCount,
                vicePrincipals,
                reason);
    }
}

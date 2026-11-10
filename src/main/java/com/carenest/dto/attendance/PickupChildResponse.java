package com.carenest.dto.attendance;

import com.carenest.utils.enums.GuardianRelationship;
import java.time.LocalDate;
import java.util.List;

/**
 * Trả trẻ hôm nay: trạng thái của trẻ và danh sách phụ huynh trên hồ sơ để đối chiếu người đón. {@code photoUrl}: đường
 * dẫn ảnh phụ huynh (tải bằng token, không đặt thẳng vào thẻ ảnh); null khi phụ huynh chưa có tài khoản hoặc chưa tải ảnh.
 */
public record PickupChildResponse(
        PickupBoardResponse.PickupRow row,
        LocalDate date,
        Long classId,
        String className,
        List<PickupGuardian> guardians) {

    public record PickupGuardian(
            int position, String fullName, GuardianRelationship relationship, String phone, String photoUrl) {}
}

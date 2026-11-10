package com.carenest.dto.child;

import com.carenest.utils.enums.Gender;
import java.time.LocalDate;
import java.util.List;

/**
 * Sửa hồ sơ đã kiểm (feature child → account để xử lý tài khoản phụ huynh rồi ghi hồ sơ). {@code campusId} = điểm trường
 * hiện tại của trẻ (người duyệt đổi SĐT theo điểm trường này). Dữ liệu cá nhân: không log.
 */
public record ChildEditDraft(
        Long childId,
        Long campusId,
        String reason,
        String fullName,
        LocalDate dateOfBirth,
        Gender gender,
        ChildProfileDraft profile,
        List<GuardianEdit> guardians) {
    public ChildEditDraft {
        guardians = List.copyOf(guardians);
    }

    public ChildEditDraft withGuardians(List<GuardianEdit> replaced) {
        return new ChildEditDraft(childId, campusId, reason, fullName, dateOfBirth, gender, profile, replaced);
    }

    @Override
    public String toString() {
        return "ChildEditDraft[childId=" + childId + ", guardians=" + guardians.size() + "]";
    }

    /**
     * Một phụ huynh sau khi sửa. {@code guardianId} = dòng có sẵn trên hồ sơ (null = thêm mới); {@code accountId} = tài
     * khoản đang liên kết với trẻ (null = chưa kích hoạt); {@code currentPhone} = SĐT đang lưu của dòng có sẵn.
     */
    public record GuardianEdit(GuardianDraft draft, Long guardianId, Long accountId, String currentPhone) {
        public GuardianEdit withDraft(GuardianDraft replaced) {
            return new GuardianEdit(replaced, guardianId, accountId, currentPhone);
        }

        @Override
        public String toString() {
            return "GuardianEdit[guardianId=" + guardianId + ", accountId=" + accountId + "]";
        }
    }
}

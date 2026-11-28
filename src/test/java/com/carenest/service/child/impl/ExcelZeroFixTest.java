package com.carenest.service.child.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.dto.child.GuardianInput;
import com.carenest.security.CurrentUser;
import com.carenest.service.child.ChildEnrollmentImportService.PreviewRow;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

/** Bù số 0 đầu SĐT/CCCD mà Excel bỏ mất khi lưu ô số. Dữ liệu giả. */
class ExcelZeroFixTest {

    private static final String NOTE = "Đã thêm số 0 đầu cho SĐT/CCCD (Excel bỏ mất). Kiểm tra lại trước khi nhập.";

    @Test
    @DisplayName("SĐT 9 số bắt đầu 3/5/7/8/9 ⇒ thêm 0; còn lại giữ nguyên")
    void phone() {
        assertThat(ExcelZeroFix.phone("916231201")).contains("0916231201");
        assertThat(ExcelZeroFix.phone("0916231201")).isEmpty();
        assertThat(ExcelZeroFix.phone("123456789")).isEmpty();
    }

    @Test
    @DisplayName("CCCD 10–11 số ⇒ đệm 0 tới 12; 9 số (mã tỉnh 001–096 chỉ mất tối đa 2 số 0) và còn lại giữ nguyên")
    void citizenId() {
        assertThat(ExcelZeroFix.citizenId("1623120001")).contains("001623120001");
        assertThat(ExcelZeroFix.citizenId("30224591837")).contains("030224591837");
        assertThat(ExcelZeroFix.citizenId("030224591837")).isEmpty();
        assertThat(ExcelZeroFix.citizenId("123456789")).isEmpty();
        assertThat(ExcelZeroFix.citizenId("12345678")).isEmpty();
    }

    @Test
    @DisplayName("Xem trước file CSV mất số 0 đầu ⇒ bù số 0, không lỗi dòng, có ghi chú")
    void previewRestoresZeros() {
        var clock = Clock.fixed(Instant.parse("2026-11-24T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        var service = new ChildEnrollmentImportServiceImpl(Mockito.mock(ChildIntakeService.class), clock);
        var actor = new CurrentUser(
                3L,
                "kc-3",
                "vp",
                "PHT giả",
                null,
                Set.of(CareNestRole.VICE_PRINCIPAL),
                Set.of(Permission.CHILD_ENROLL));
        String csv = String.join(
                "\n",
                "Họ tên trẻ,Ngày sinh (dd/mm/yyyy),Giới tính (Nam/Nữ),Họ tên phụ huynh,Quan hệ,Số điện thoại,"
                        + "Số CCCD phụ huynh,Sinh non (Có/Không),Tuần thai khi sinh,Dân tộc,Đối tượng,"
                        + "Nơi ở - Tỉnh/thành,Nơi ở - Phường/xã,Nơi ở - Số nhà/thôn/xóm,Tiền sử bản thân,"
                        + "Tiền sử gia đình,Tiếp xúc người bệnh lao",
                "Bé An giả,15/03/2022,Nam,Mẹ giả,Mẹ,916231201,1623120001,Không,39,Kinh,Thường,"
                        + "Tỉnh giả,Phường giả,Số 1,Không,Không,Không");
        var file = new MockMultipartFile("file", "tre.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        PreviewRow row = service.preview(actor, file).rows().getFirst();

        GuardianInput guardian = row.payload().child().guardians().getFirst();
        assertThat(guardian.phone()).isEqualTo("0916231201");
        assertThat(guardian.idNumber()).isEqualTo("001623120001");
        assertThat(row.errors()).isEmpty();
        assertThat(row.notes()).containsOnlyOnce(NOTE);
    }
}

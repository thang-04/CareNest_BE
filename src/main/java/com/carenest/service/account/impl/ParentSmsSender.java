package com.carenest.service.account.impl;

import com.carenest.entity.account.ParentSmsLog;
import com.carenest.entity.account.UserAccount;
import com.carenest.integration.sms.FakeSmsGateway;
import com.carenest.integration.sms.SmsGateway;
import com.carenest.integration.sms.SmsProperties;
import com.carenest.repository.account.ParentSmsLogRepository;
import com.carenest.utils.enums.SmsPurpose;
import com.carenest.utils.enums.SmsStatus;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Gửi SMS tài khoản phụ huynh (L-15): tên đăng nhập + mật khẩu tạm, ghi {@code parent_sms_logs} (không lưu nội dung).
 * Gọi sau khi giao dịch DB đã commit; SMS lỗi chỉ trả FAILED, không hủy tài khoản hay hồ sơ (FE cho gửi lại mật khẩu).
 * Tin không dấu để vừa một SMS; mật khẩu không log.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ParentSmsSender {
    private static final String LOG_PREFIX = "[ParentSmsSender]";

    private static final SmsGateway PASSWORD_SAFE_GATEWAY = new FakeSmsGateway();

    private final SmsGateway smsGateway;
    private final SmsProperties smsProperties;
    private final ParentSmsLogRepository parentSmsLogRepository;
    private final Clock clock;

    SmsStatus sendActivation(UserAccount account, String temporaryPassword) {
        String text = "CareNest: Tai khoan phu huynh da duoc tao. Ten dang nhap: " + account.getUsername()
                + ". Mat khau tam: " + temporaryPassword + ". Doi mat khau o lan dang nhap dau.";
        return send(passwordGateway(), account, SmsPurpose.ACTIVATION, text);
    }

    /** Đổi SĐT được duyệt (G94-180/AUTH-21): {@code account} đã mang tên đăng nhập mới nên tin tới SĐT mới. */
    SmsStatus sendUsernameChanged(UserAccount account, String temporaryPassword) {
        String text = "CareNest: So dien thoai dang nhap da doi. Ten dang nhap moi: " + account.getUsername()
                + ". Mat khau tam: " + temporaryPassword + ". Doi mat khau o lan dang nhap dau.";
        return send(passwordGateway(), account, SmsPurpose.USERNAME_CHANGED, text);
    }

    /** AUTH-18: tin chứa mã kích hoạt có hạn (không có mật khẩu) ⇒ đi qua nhà cung cấp đang cấu hình. */
    SmsStatus sendActivationCode(UserAccount account, String code) {
        String text = "CareNest: Ma kich hoat tai khoan phu huynh: " + code + ". Ten dang nhap: "
                + account.getUsername() + ". Mo ung dung CareNest, chon Kich hoat tai khoan, nhap ma trong 72 gio.";
        return send(smsGateway, account, SmsPurpose.ACTIVATION, text);
    }

    /** Nhà cung cấp SMS thật đang bật ⇒ tài khoản phụ huynh mới dùng mã kích hoạt thay mật khẩu tạm. */
    boolean activationCodeMode() {
        return smsProperties.usesRealProvider();
    }

    // AUTH-18: tin chứa mật khẩu không bao giờ đi qua nhà cung cấp thật (luồng đó dùng mã kích hoạt)
    private SmsGateway passwordGateway() {
        return smsProperties.usesRealProvider() ? PASSWORD_SAFE_GATEWAY : smsGateway;
    }

    private SmsStatus send(SmsGateway gateway, UserAccount account, SmsPurpose purpose, String text) {
        String logPrefix = LOG_PREFIX + "|send|userId=" + account.getId() + "|purpose=" + purpose;
        SmsStatus status;
        try {
            gateway.send(account.getUsername(), text);
            status = SmsStatus.SENT;
        } catch (RuntimeException ex) {
            log.warn("{}|failed|type={}", logPrefix, ex.getClass().getSimpleName());
            status = SmsStatus.FAILED;
        }
        try {
            parentSmsLogRepository.save(
                    ParentSmsLog.of(account.getId(), account.getUsername(), purpose, status, clock.instant()));
        } catch (RuntimeException ex) {
            // Tài khoản, hồ sơ đã lưu; chỉ mất dòng log lần gửi (FE thấy trạng thái trong response)
            log.error("{}|smsLogNotSaved|type={}", logPrefix, ex.getClass().getSimpleName(), ex);
        }
        log.info("{}|status={}", logPrefix, status);
        return status;
    }
}

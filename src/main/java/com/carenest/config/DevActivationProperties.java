package com.carenest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * CHỈ DÙNG KHI DEV để thử kích hoạt năm học trước hạn (bật bằng biến môi trường, mặc định tắt):
 * skipDateWindow bỏ hạn "từ 7 ngày trước ngày bắt đầu"; skipVicePrincipalStep bỏ bước phân công PHT
 * (màn Phân công PHT chưa có BE); skipCloseEndDate cho kết thúc năm đang hoạt động trước ngày kết thúc.
 * Các bước bắt buộc khác vẫn kiểm.
 */
@ConfigurationProperties("carenest.dev.activation")
public record DevActivationProperties(
        @DefaultValue("false") boolean skipDateWindow,
        @DefaultValue("false") boolean skipVicePrincipalStep,
        @DefaultValue("false") boolean skipCloseEndDate) {}

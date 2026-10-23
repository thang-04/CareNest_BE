package com.carenest.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** EDU-10: khóa "Đang sửa" tự nhả sau {@code editLockTtl} kể từ lần lưu cuối (Tiền chốt 30 phút, 23/10). */
@ConfigurationProperties("carenest.edu-plan")
public record EduPlanProperties(@DefaultValue("30m") Duration editLockTtl) {}

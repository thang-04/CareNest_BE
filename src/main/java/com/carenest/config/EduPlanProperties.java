package com.carenest.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** EDU-10: khóa "Đang sửa" tự nhả sau {@code editLockTtl} kể từ lần liên lạc cuối (Tiền duyệt 27/10). */
@ConfigurationProperties("carenest.edu-plan")
public record EduPlanProperties(@DefaultValue("90s") Duration editLockTtl) {}

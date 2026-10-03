package com.carenest.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// Bật @Async (gửi mail OTP) và @Scheduled (dọn token hết hạn) mà không sửa class main
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncSchedulingConfig {
}

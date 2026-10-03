/**
 * REST endpoints. Nhận request, validate đầu vào (@Valid), gọi service, trả ResponseJson.
 * Không gọi repository, không chứa logic nghiệp vụ. Chia sub-package theo chức năng (attendance, child, meals, ...).
 * Prefix API được gắn tự động từ carenest.api.prefix (WebMvcConfig).
 */
package com.carenest.controller;

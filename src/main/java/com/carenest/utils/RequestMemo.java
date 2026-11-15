package com.carenest.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Nhớ kết quả tra cứu trong đúng một request đọc (HTTP GET): một lần mở chi tiết kế hoạch từng tra phân công/cấu hình duyệt
 * hàng trăm lần (FE-BUG-261111-dashboard-reload-burst). Chỉ dùng cho GET vì request đọc không ghi ⇒ không có dữ liệu cũ;
 * ngoài request web (test, job) thì luôn tra thẳng.
 */
public final class RequestMemo {
    private static final String ATTRIBUTE = RequestMemo.class.getName();

    private RequestMemo() {}

    @SuppressWarnings("unchecked")
    public static <T> T get(String key, Supplier<T> loader) {
        Map<String, Object> memo = memo();
        if (memo == null) return loader.get();
        if (memo.containsKey(key)) return (T) memo.get(key);
        T value = loader.get();
        memo.put(key, value);
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> memo() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)
                || !"GET".equals(servlet.getRequest().getMethod())) return null;
        Object current = attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (current instanceof Map<?, ?> map) return (Map<String, Object>) map;
        Map<String, Object> created = new HashMap<>();
        attributes.setAttribute(ATTRIBUTE, created, RequestAttributes.SCOPE_REQUEST);
        return created;
    }
}

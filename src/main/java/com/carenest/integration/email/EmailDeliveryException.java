package com.carenest.integration.email;

/** Gửi email không thành công; nơi gọi quyết định ghi trạng thái lỗi hay hủy nghiệp vụ. */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message) {
        super(message);
    }
}

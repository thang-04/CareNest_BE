package com.carenest.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // Chung
    UNCATEGORIZED(9999, "Lỗi hệ thống, vui lòng thử lại sau", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_REQUEST(1000, "Dữ liệu không hợp lệ", HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(1001, "Dữ liệu gửi lên sai định dạng", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(1002, "Không tìm thấy tài nguyên yêu cầu", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(1003, "Phương thức không được hỗ trợ", HttpStatus.METHOD_NOT_ALLOWED),
    DATA_CONFLICT(1004, "Dữ liệu bị trùng hoặc vi phạm ràng buộc", HttpStatus.CONFLICT),
    UNSUPPORTED_MEDIA_TYPE(1005, "Kiểu dữ liệu gửi lên không được hỗ trợ", HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    // Xác thực & phân quyền
    UNAUTHENTICATED(1100, "Bạn chưa đăng nhập hoặc phiên đăng nhập đã hết hạn", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(1101, "Tài khoản hoặc mật khẩu không chính xác", HttpStatus.UNAUTHORIZED),
    ACCOUNT_DISABLED(1102, "Tài khoản đã bị khoá", HttpStatus.FORBIDDEN),
    ACCESS_DENIED(1103, "Bạn không có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN),
    INVALID_TOKEN(1104, "Token không hợp lệ hoặc đã hết hạn", HttpStatus.UNAUTHORIZED),

    // Người dùng
    EMAIL_EXISTED(1200, "Email đã được sử dụng", HttpStatus.CONFLICT),
    PHONE_EXISTED(1201, "Số điện thoại đã được sử dụng", HttpStatus.CONFLICT),
    PASSWORD_CONFIRM_NOT_MATCH(1202, "Mật khẩu xác nhận không khớp", HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND(1203, "Tài khoản không tồn tại trong hệ thống", HttpStatus.NOT_FOUND),
    OLD_PASSWORD_INCORRECT(1204, "Mật khẩu hiện tại không chính xác", HttpStatus.BAD_REQUEST),
    NEW_PASSWORD_SAME_AS_OLD(1205, "Mật khẩu mới phải khác mật khẩu hiện tại", HttpStatus.BAD_REQUEST),
    PARENT_NOT_MANAGED(1206, "Bạn chỉ có thể thao tác với phụ huynh của trẻ do mình quản lý", HttpStatus.FORBIDDEN),
    ROLE_NOT_ASSIGNABLE(1207, "Chỉ được tạo tài khoản Phó hiệu trưởng, Giáo viên hoặc Nhân viên", HttpStatus.BAD_REQUEST),
    TEACHER_NOT_FOUND(1208, "Giáo viên không tồn tại trong hệ thống", HttpStatus.NOT_FOUND),
    CANNOT_CHANGE_OWN_STATUS(1209, "Bạn không thể tự khoá hoặc mở khoá tài khoản của mình", HttpStatus.BAD_REQUEST),
    USER_IN_USE(1210, "Tài khoản đã có dữ liệu nên không thể xoá. Hãy khoá tài khoản thay vì xoá", HttpStatus.CONFLICT),
    EMAIL_REQUIRED(1211, "Tài khoản nhân sự phải có email để đăng nhập", HttpStatus.BAD_REQUEST),
    PHONE_REQUIRED(1212, "Tài khoản phụ huynh phải có số điện thoại để đăng nhập", HttpStatus.BAD_REQUEST),
    NOT_STAFF_ACCOUNT(1213, "Chỉ đổi vai trò được cho tài khoản nhân sự", HttpStatus.BAD_REQUEST),
    CANNOT_MANAGE_SELF(1214, "Bạn không thể thực hiện thao tác này trên tài khoản của mình", HttpStatus.BAD_REQUEST),

    // OTP quên mật khẩu
    OTP_INVALID(1300, "Mã OTP không chính xác", HttpStatus.BAD_REQUEST),
    OTP_ALREADY_SENT(1301, "Mã OTP đã được gửi trước đó. Vui lòng thử lại sau %d giây", HttpStatus.TOO_MANY_REQUESTS),
    OTP_EXPIRED(1302, "Mã OTP đã hết hạn", HttpStatus.BAD_REQUEST),
    OTP_NOT_VERIFIED(1303, "Vui lòng xác minh mã OTP trước khi đổi mật khẩu", HttpStatus.FORBIDDEN),
    SMS_NOT_SUPPORTED(1304, "Chức năng gửi OTP qua SMS đang được phát triển, vui lòng dùng email", HttpStatus.NOT_IMPLEMENTED),
    MAIL_SEND_FAILED(1305, "Không thể gửi email. Vui lòng thử lại sau", HttpStatus.SERVICE_UNAVAILABLE),

    // Hình ảnh
    INVALID_IMAGE(1400, "Ảnh không hợp lệ. Chỉ chấp nhận JPG, PNG hoặc WebP", HttpStatus.BAD_REQUEST),
    IMAGE_TOO_LARGE(1401, "Ảnh tối đa 5MB", HttpStatus.PAYLOAD_TOO_LARGE),
    STORAGE_FAILED(1402, "Không thể lưu ảnh. Vui lòng thử lại sau", HttpStatus.SERVICE_UNAVAILABLE),
    IMAGE_NOT_FOUND(1403, "Ảnh không tồn tại", HttpStatus.NOT_FOUND),

    // Trẻ & người đón
    CHILD_NOT_FOUND(1500, "Trẻ không tồn tại", HttpStatus.NOT_FOUND),
    PICKUP_PERSON_NOT_FOUND(1501, "Người đón trẻ không tồn tại", HttpStatus.NOT_FOUND);

    private final int code;
    private final String message;
    private final HttpStatus status;

    ErrorCode(int code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }
}

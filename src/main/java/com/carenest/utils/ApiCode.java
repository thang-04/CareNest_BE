package com.carenest.utils;

import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Mã kết quả dùng trong {@link ResponseJson}. {@code code} luôn trùng HTTP status của response; {@code errorCode} là
 * mã lỗi nghiệp vụ 4 số theo phân hệ (docs/contracts/ERROR_CONTRACT.md), {@code null} với mã chung và mã thành công.
 * Lỗi mới: thêm một dòng vào đúng dải phân hệ; service không tự viết câu lỗi.
 */
@Getter
public enum ApiCode {
    // ========== Mã chung (không có mã lỗi nghiệp vụ) ==========
    SUCCESSFUL(HttpStatus.OK, "Success"),
    CREATED(HttpStatus.CREATED, "Created"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Bad request"),
    /** Alias của BAD_REQUEST, giữ tương thích tài liệu kiến trúc. */
    UNSUCCESSFUL(HttpStatus.BAD_REQUEST, "Unsuccessful"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),
    /** Trùng dữ liệu hoặc sai trạng thái nghiệp vụ (vd. số suất ăn đã được xác nhận). */
    CONFLICT(HttpStatus.CONFLICT, "Conflict"),
    PRECONDITION_REQUIRED(HttpStatus.PRECONDITION_REQUIRED, "Password change required"),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "File too large"),
    /** Dịch vụ ngoài (AI, storage) không khả dụng. */
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable"),
    /** Dịch vụ ngoài (AI) quá thời gian chờ. */
    GATEWAY_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "Upstream timeout"),

    // ========== 10xx XÁC THỰC ==========
    INVALID_CREDENTIALS(
            1001, HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng (Số điện thoại hoặc mật khẩu không đúng)"),
    UNAUTHENTICATED(1002, HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại"),
    WEB_SESSION_INVALID(1003, HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ hoặc đã hết hạn"),
    AUTH_REQUEST_NOT_FOUND(1004, HttpStatus.NOT_FOUND, "Yêu cầu đăng nhập không tồn tại hoặc đã hết hạn"),
    INVALID_AUTH_REQUEST(1005, HttpStatus.BAD_REQUEST, "Yêu cầu đăng nhập không hợp lệ"),
    OTP_INVALID_OR_EXPIRED(1006, HttpStatus.BAD_REQUEST, "Mã xác nhận không đúng hoặc đã hết hạn"),
    OTP_RESEND_TOO_SOON(1007, HttpStatus.TOO_MANY_REQUESTS, "Vui lòng chờ trước khi yêu cầu mã mới"),
    RECOVERY_REQUEST_UNAVAILABLE(1008, HttpStatus.CONFLICT, "Yêu cầu khôi phục mật khẩu không còn hiệu lực"),
    PASSWORD_POLICY_VIOLATION(1009, HttpStatus.BAD_REQUEST, "Mật khẩu mới chưa đáp ứng chính sách mật khẩu"),
    PASSWORD_CHANGE_REQUIRED(1010, HttpStatus.PRECONDITION_REQUIRED, "Cần đổi mật khẩu tạm trước khi tiếp tục"),
    CSRF_TOKEN_UNAVAILABLE(1011, HttpStatus.INTERNAL_SERVER_ERROR, "Không tạo được phiên bảo mật, vui lòng thử lại"),
    WEB_AUTH_GATEWAY_DISABLED(1012, HttpStatus.SERVICE_UNAVAILABLE, "Đăng nhập web đang tạm tắt"),
    AUTH_SERVICE_UNAVAILABLE(
            1013, HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ đăng nhập đang gián đoạn, vui lòng thử lại sau"),
    ACCOUNT_NOT_PROVISIONED(1014, HttpStatus.FORBIDDEN, "Tài khoản chưa được cấp quyền truy cập hệ thống"),
    ACCOUNT_INACTIVE(1015, HttpStatus.FORBIDDEN, "Tài khoản đã bị khóa"),

    // ========== 11xx TÀI KHOẢN & PHÂN QUYỀN ==========
    FORBIDDEN(1101, HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này"),
    PRINCIPAL_ONLY(1102, HttpStatus.FORBIDDEN, "Chỉ Hiệu trưởng được {0}"),
    ACCOUNT_NOT_FOUND(1103, HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản"),
    ROLE_NOT_CREATABLE(1104, HttpStatus.BAD_REQUEST, "Không tạo được tài khoản với vai trò này"),
    TEMP_PASSWORD_REQUIRED(1105, HttpStatus.BAD_REQUEST, "Cần nhập mật khẩu tạm khi không gửi được email thiết lập"),
    USERNAME_OR_EMAIL_EXISTS(1106, HttpStatus.CONFLICT, "Số điện thoại hoặc email đã tồn tại"),
    ACCOUNT_EXISTS(1107, HttpStatus.CONFLICT, "Tài khoản đã tồn tại"),
    CANNOT_CHANGE_OWN_STATUS(1108, HttpStatus.BAD_REQUEST, "Không tự khóa/mở tài khoản của chính mình"),
    IDENTITY_DATA_REJECTED(1109, HttpStatus.BAD_REQUEST, "Thông tin tài khoản bị từ chối, kiểm tra mật khẩu và họ tên"),
    IDENTITY_SYNC_FAILED(
            1110,
            HttpStatus.SERVICE_UNAVAILABLE,
            "Đã lưu trạng thái tài khoản nhưng chưa đồng bộ được, vui lòng thử lại"),
    IDENTITY_PROVIDER_UNAVAILABLE(
            1111, HttpStatus.SERVICE_UNAVAILABLE, "Hệ thống tài khoản đang gián đoạn, vui lòng thử lại sau"),
    ACCOUNT_MANAGE_DENIED(1112, HttpStatus.FORBIDDEN, "Bạn không có quyền quản lý tài khoản này"),
    ACCOUNT_ROLE_CREATE_DENIED(1113, HttpStatus.FORBIDDEN, "Bạn không có quyền tạo tài khoản với vai trò này"),
    CANNOT_MANAGE_OWN_ACCOUNT(1114, HttpStatus.BAD_REQUEST, "Không tự quản lý tài khoản của chính mình tại đây"),
    USE_CHANGE_PASSWORD_FOR_SELF(
            1115, HttpStatus.BAD_REQUEST, "Dùng chức năng đổi mật khẩu cho tài khoản của chính mình"),
    PASSWORD_RESET_SESSION_REVOKE_FAILED(
            1116,
            HttpStatus.SERVICE_UNAVAILABLE,
            "Đã đặt mật khẩu tạm nhưng chưa đăng xuất được các phiên cũ, vui lòng thử lại"),
    ROLE_PERMISSIONS_FIXED(1117, HttpStatus.FORBIDDEN, "Quyền của vai trò này cố định, không chỉnh được"),
    PERMISSION_NOT_CONFIGURABLE_FOR_ROLE(1118, HttpStatus.BAD_REQUEST, "Quyền {0} không cấu hình được cho vai trò {1}"),
    CANNOT_CHANGE_OWN_PERMISSIONS(1119, HttpStatus.BAD_REQUEST, "Không tự đổi quyền của chính mình"),
    PERMISSIONS_NOT_OVERRIDABLE(1120, HttpStatus.FORBIDDEN, "Không cấp ngoại lệ quyền cho tài khoản này"),
    PERMISSION_LOG_FILTER_INVALID(
            1121, HttpStatus.BAD_REQUEST, "Chỉ lọc theo vai trò hoặc theo tài khoản, không chọn cả hai"),
    PERMISSION_DUPLICATED(1122, HttpStatus.BAD_REQUEST, "Quyền {0} bị chọn trùng"),
    PERMISSION_NOT_CONFIGURABLE_FOR_ACCOUNT(
            1123, HttpStatus.BAD_REQUEST, "Quyền {0} không cấu hình được cho tài khoản này"),
    PHONE_NUMBER_INVALID(1124, HttpStatus.BAD_REQUEST, "Số điện thoại không hợp lệ"),
    NOT_PARENT_ACCOUNT(1126, HttpStatus.BAD_REQUEST, "Tài khoản này không phải tài khoản phụ huynh"),
    STAFF_ROLE_NOT_REVOCABLE(1127, HttpStatus.BAD_REQUEST, "Tài khoản này không có vai trò nhân sự thu hồi được"),
    STAFF_ROLE_REVOKE_REQUIRES_CHILD(
            1128,
            HttpStatus.BAD_REQUEST,
            "Tài khoản không còn con đang học, hãy khóa tài khoản thay vì thu hồi vai trò nhân sự"),
    ACCOUNT_CONFIRMATION_REQUIRED(
            1129,
            HttpStatus.CONFLICT,
            "Số điện thoại đã là tài khoản phụ huynh, hãy tra số và xác nhận đúng người trước khi thêm vai trò nhân sự"),

    // ========== 20xx NĂM HỌC ==========
    SCHOOL_YEAR_NOT_FOUND(2001, HttpStatus.NOT_FOUND, "Không tìm thấy năm học"),
    NO_ACTIVE_SCHOOL_YEAR(2002, HttpStatus.NOT_FOUND, "Chưa có năm học đang hoạt động"),
    SCHOOL_YEAR_CLOSED(2003, HttpStatus.CONFLICT, "Năm học {0} đã kết thúc, chỉ xem được"),
    SCHOOL_YEAR_NAME_INVALID(
            2004, HttpStatus.BAD_REQUEST, "Tên năm học có dạng NNNN-NNNN, năm sau lớn hơn năm trước 1 năm"),
    END_DATE_BEFORE_START(2005, HttpStatus.BAD_REQUEST, "Ngày kết thúc phải sau ngày bắt đầu"),
    SCHOOL_YEAR_EXISTS(2006, HttpStatus.CONFLICT, "Năm học {0} đã tồn tại"),
    SCHOOL_YEAR_DATES_OVERLAP(2007, HttpStatus.CONFLICT, "Khoảng thời gian trùng với năm học {0}"),
    SCHOOL_YEAR_NAME_LOCKED(2008, HttpStatus.CONFLICT, "Chỉ đổi được tên năm học khi năm học chưa bắt đầu"),
    SCHOOL_YEAR_HAS_CLASSES(2009, HttpStatus.CONFLICT, "Năm học đã có lớp, không đổi được tên"),
    SCHOOL_YEAR_NOT_DELETABLE(2010, HttpStatus.CONFLICT, "Chỉ xóa được năm học chưa bắt đầu"),
    SCHOOL_YEAR_IN_USE(2011, HttpStatus.CONFLICT, "Năm học đã có dữ liệu liên quan, không xóa được"),
    SCHOOL_YEAR_NOT_ACTIVATABLE(2012, HttpStatus.CONFLICT, "Chỉ kích hoạt được năm học chưa bắt đầu"),
    ACTIVATE_EARLIER_YEAR_FIRST(2013, HttpStatus.CONFLICT, "Kích hoạt năm học {0} trước"),
    ACTIVATION_CONFIRM_MISMATCH(2014, HttpStatus.BAD_REQUEST, "Gõ đúng tên năm học để xác nhận"),
    ACTIVATION_TOO_EARLY(2015, HttpStatus.CONFLICT, "Chỉ kích hoạt được từ ngày {0}"),
    SCHOOL_YEAR_NOT_READY(2016, HttpStatus.CONFLICT, "Năm học chưa đủ điều kiện kích hoạt: {0}"),
    SCHOOL_YEAR_NOT_CLOSABLE(2017, HttpStatus.CONFLICT, "Chỉ kết thúc được năm học đang hoạt động"),
    SCHOOL_YEAR_CLOSE_TOO_EARLY(2018, HttpStatus.CONFLICT, "Chỉ kết thúc được năm học sau ngày kết thúc {0}"),
    SCHOOL_YEAR_OUT_OF_SEQUENCE(2019, HttpStatus.BAD_REQUEST, "Năm học tiếp theo phải là {0} (sau năm {1})"),
    SCHOOL_YEAR_START_BEFORE_LATEST_END(
            2020, HttpStatus.BAD_REQUEST, "Ngày bắt đầu phải sau ngày kết thúc năm học {0} ({1})"),
    SEMESTER_OUT_OF_YEAR(2021, HttpStatus.BAD_REQUEST, "{0} phải nằm trong năm học ({1} – {2})"),
    YEAR_COPY_TARGET_NOT_PLANNED(2022, HttpStatus.CONFLICT, "Chỉ sao chép vào năm học chưa bắt đầu ({0} đang {1})"),
    YEAR_COPY_PARTS_REQUIRED(2023, HttpStatus.BAD_REQUEST, "Chọn ít nhất một phần để sao chép"),
    SEMESTER_START_AFTER_END(2024, HttpStatus.BAD_REQUEST, "{0}: ngày bắt đầu không được sau ngày kết thúc"),
    SEMESTER_OVERLAP(2025, HttpStatus.BAD_REQUEST, "Học kỳ II phải bắt đầu sau ngày kết thúc học kỳ I ({0})"),
    SEMESTER_DATE_REQUIRED(2026, HttpStatus.BAD_REQUEST, "Chọn {0}"),

    // ========== 21xx ĐIỂM TRƯỜNG & PHÒNG ==========
    SCHOOL_CONFIG_ACCESS_DENIED(2101, HttpStatus.FORBIDDEN, "Bạn không có quyền xem cấu hình trường"),
    CAMPUS_ACCESS_DENIED(2102, HttpStatus.FORBIDDEN, "Bạn không có quyền với điểm trường này"),
    CAMPUS_NOT_FOUND(2103, HttpStatus.NOT_FOUND, "Không tìm thấy điểm trường"),
    ROOM_NOT_FOUND(2104, HttpStatus.NOT_FOUND, "Không tìm thấy phòng"),
    CAMPUS_CODE_EXISTS(2105, HttpStatus.CONFLICT, "Mã điểm trường đã tồn tại"),
    CAMPUS_NAME_EXISTS(2106, HttpStatus.CONFLICT, "Tên điểm trường đã tồn tại"),
    MAIN_CAMPUS_REQUIRED(
            2107, HttpStatus.CONFLICT, "Trường cần một điểm trường chính, chọn điểm trường chính khác trước"),
    MAIN_CAMPUS_NOT_DELETABLE(2108, HttpStatus.CONFLICT, "Không xóa được điểm trường chính"),
    CAMPUS_HAS_DEPENDENTS(2109, HttpStatus.CONFLICT, "Điểm trường đã có {0}, không xóa được"),
    MAIN_CAMPUS_NOT_DEACTIVATABLE(2110, HttpStatus.CONFLICT, "Không ngừng hoạt động được điểm trường chính"),
    CAMPUS_HAS_ACTIVE_CLASSES(
            2111,
            HttpStatus.CONFLICT,
            "Điểm trường còn lớp ở năm học đang hoạt động hoặc sắp diễn ra. Chuyển hoặc xóa lớp trước"),
    CAMPUS_HAS_VICE_PRINCIPALS(
            2112,
            HttpStatus.CONFLICT,
            "Điểm trường còn PHT phụ trách: {0}. Gỡ phân công PHT trước khi ngừng hoạt động"),
    CAMPUS_INACTIVE(2113, HttpStatus.CONFLICT, "Điểm trường đã ngừng hoạt động, chọn điểm trường khác"),
    ROOM_CODE_EXISTS(2114, HttpStatus.CONFLICT, "Điểm trường đã có phòng cùng mã"),
    ROOM_NAME_EXISTS(2115, HttpStatus.CONFLICT, "Điểm trường đã có phòng cùng tên"),
    ROOM_CAPACITY_REQUIRED(2116, HttpStatus.BAD_REQUEST, "Nhập sức chứa của phòng học"),
    ROOM_CAPACITY_BELOW_CLASS_SIZE(
            2117, HttpStatus.CONFLICT, "Lớp đang dùng phòng có sĩ số tối đa {0}, sức chứa không được nhỏ hơn"),
    ROOM_IN_USE_BY_CLASS(2118, HttpStatus.CONFLICT, "Phòng đang là phòng học của {0}, không thể {1}"),
    ROOM_MANAGER_INVALID(2119, HttpStatus.BAD_REQUEST, "Người phụ trách phải là nhân sự của điểm trường này"),
    ROOM_NOT_IN_CAMPUS(2120, HttpStatus.BAD_REQUEST, "Phòng không thuộc điểm trường đã chọn"),
    ROOM_INACTIVE(2121, HttpStatus.CONFLICT, "Phòng đã ngừng sử dụng"),
    ROOM_TAKEN(2122, HttpStatus.CONFLICT, "Phòng đã được lớp khác dùng trong năm học này"),

    // ========== 22xx NHÓM TUỔI & LỚP ==========
    CLASS_ACCESS_DENIED(2201, HttpStatus.FORBIDDEN, "Bạn không có quyền với lớp này"),
    AGE_GROUP_NOT_FOUND(2202, HttpStatus.NOT_FOUND, "Không tìm thấy nhóm tuổi"),
    CLASS_NOT_FOUND(2203, HttpStatus.NOT_FOUND, "Không tìm thấy lớp"),
    AGE_GROUP_NAME_EXISTS(2204, HttpStatus.CONFLICT, "Tên nhóm tuổi đã tồn tại"),
    AGE_RANGE_INVALID(2205, HttpStatus.BAD_REQUEST, "Độ tuổi đến phải lớn hơn độ tuổi từ"),
    AGE_RANGE_OVERLAP(2206, HttpStatus.CONFLICT, "Độ tuổi trùng với nhóm {0}"),
    AGE_GROUP_HAS_DEPENDENTS(2207, HttpStatus.CONFLICT, "Nhóm tuổi đã có {0}, không xóa được"),
    AGE_GROUP_MAX_SIZE_CONFLICT(2208, HttpStatus.CONFLICT, "Không hạ được sĩ số tối đa: {0} đang vượt {1} trẻ"),
    CLASS_NAME_EXISTS(2209, HttpStatus.CONFLICT, "Điểm trường đã có lớp cùng tên trong năm học này"),
    CLASS_YEAR_IMMUTABLE(2210, HttpStatus.BAD_REQUEST, "Không chuyển lớp sang năm học khác được"),
    CLASS_AGE_GROUP_LOCKED(2211, HttpStatus.CONFLICT, "Lớp đã có trẻ, không đổi được nhóm tuổi"),
    CLASS_CAMPUS_LOCKED(2212, HttpStatus.CONFLICT, "Lớp đã có trẻ hoặc giáo viên, không đổi được điểm trường"),
    CLASS_HAS_DEPENDENTS(2213, HttpStatus.CONFLICT, "Lớp đã có {0}, không xóa được"),
    CLASS_MAX_SIZE_OUT_OF_RANGE(2214, HttpStatus.BAD_REQUEST, "Sĩ số tối đa từ {0} đến {1}"),
    COPY_SOURCE_YEAR_INVALID(2215, HttpStatus.BAD_REQUEST, "Chọn năm học khác với năm đang cấu hình để sao chép"),
    CLASS_PLACED_AGE_GROUP_LOCKED(2216, HttpStatus.CONFLICT, "Lớp đã có trẻ được xếp vào, không đổi được nhóm tuổi"),
    CLASS_PLACED_CAMPUS_LOCKED(2217, HttpStatus.CONFLICT, "Lớp đã có trẻ được xếp vào, không đổi được điểm trường"),
    CLASS_HAS_PLACED_CHILDREN(2218, HttpStatus.CONFLICT, "Lớp đã có trẻ được xếp vào năm mới, không xóa được"),

    // ========== 23xx PHÂN CÔNG GIÁO VIÊN / TỔ TRƯỞNG ==========
    REPLACEMENT_TEACHER_REQUIRED(2301, HttpStatus.BAD_REQUEST, "Năm học đang hoạt động: chọn giáo viên thay thế"),
    TEACHERS_MUST_DIFFER(2302, HttpStatus.BAD_REQUEST, "Chọn 2 giáo viên khác nhau cho lớp"),
    HOMEROOM_NOT_IN_CLASS(2303, HttpStatus.BAD_REQUEST, "Giáo viên chủ nhiệm phải là một trong các giáo viên của lớp"),
    TEACHER_WRONG_CAMPUS(2304, HttpStatus.BAD_REQUEST, "Chỉ phân công giáo viên thuộc điểm trường của lớp"),
    TEACHER_ACCOUNT_LOCKED(2305, HttpStatus.CONFLICT, "Tài khoản giáo viên đã bị khóa"),
    TEACHER_ALREADY_ASSIGNED(2306, HttpStatus.CONFLICT, "Giáo viên {0} đang dạy lớp {1}, gỡ ở lớp đó trước"),
    TEAM_LEADER_INVALID(2307, HttpStatus.BAD_REQUEST, "Tổ trưởng phải là giáo viên đang làm việc"),
    STAFF_ASSIGNMENT_NOT_FOUND(2308, HttpStatus.NOT_FOUND, "Không tìm thấy phân công"),
    STAFF_ASSIGNMENT_MANAGE_DENIED(2309, HttpStatus.FORBIDDEN, "Bạn không có quyền quản lý phân công này"),
    CANNOT_CHANGE_OWN_ASSIGNMENTS(2310, HttpStatus.BAD_REQUEST, "Không tự thay đổi phân công của chính mình"),
    ACCOUNT_NOT_ASSIGNABLE(2311, HttpStatus.BAD_REQUEST, "Tài khoản này không phân công được"),
    ROLE_HAS_NO_ASSIGNMENT(2312, HttpStatus.BAD_REQUEST, "Vai trò này không có phân công nhân sự"),
    PRINCIPAL_ASSIGNMENT_SCHOOL_WIDE(
            2313, HttpStatus.BAD_REQUEST, "Phân công Hiệu trưởng áp dụng toàn trường, không chọn điểm trường hay lớp"),
    ASSIGNMENT_CAMPUS_REQUIRED(2314, HttpStatus.BAD_REQUEST, "Vai trò này phải phân công theo điểm trường"),
    ASSIGNMENT_CLASS_REQUIRED(2315, HttpStatus.BAD_REQUEST, "Giáo viên phải được phân công vào một lớp"),
    ASSIGNMENT_END_BEFORE_START(2316, HttpStatus.BAD_REQUEST, "Ngày kết thúc phân công không được trước ngày bắt đầu"),
    ASSIGNMENT_OVERLAP(2317, HttpStatus.CONFLICT, "Nhân sự đã có phân công còn hiệu lực cho phạm vi này"),
    ASSIGNMENT_ALREADY_ENDED(2318, HttpStatus.CONFLICT, "Phân công đã kết thúc"),
    TEAM_LEADER_ASSIGNMENT_NOT_HERE(
            2319, HttpStatus.BAD_REQUEST, "Phân công Tổ trưởng thực hiện ở màn phân công giáo viên theo năm học"),
    VICE_PRINCIPAL_INVALID(2320, HttpStatus.BAD_REQUEST, "Chỉ phân công tài khoản Phó hiệu trưởng"),
    VICE_PRINCIPAL_ACCOUNT_LOCKED(2321, HttpStatus.CONFLICT, "Tài khoản Phó hiệu trưởng đã bị khóa"),

    // ========== 24xx XẾP LỚP & CHUYỂN NĂM ==========
    PLACEMENT_ACCESS_DENIED(2401, HttpStatus.FORBIDDEN, "Bạn không có quyền xếp lớp"),
    CHILD_NOT_IN_ACTIVE_YEAR(2402, HttpStatus.BAD_REQUEST, "Trẻ {0} không thuộc lớp nào của năm học đang hoạt động"),
    CLASS_WITHOUT_AGE_GROUP(2403, HttpStatus.BAD_REQUEST, "{0} chưa có nhóm tuổi, cập nhật lớp trước khi xếp"),
    GRADUATION_NOT_ALLOWED(2404, HttpStatus.BAD_REQUEST, "Chỉ trẻ thuộc nhóm tuổi lớn nhất được ra trường"),
    OLDEST_GROUP_GRADUATE_OR_STAY(2405, HttpStatus.BAD_REQUEST, "Trẻ nhóm tuổi lớn nhất chỉ chọn Ra trường hoặc Ở lại"),
    TARGET_CLASS_REQUIRED(2406, HttpStatus.BAD_REQUEST, "Chọn lớp của năm học mới"),
    PLACEMENT_WRONG_AGE_GROUP(2407, HttpStatus.BAD_REQUEST, "{0} không đúng nhóm tuổi cho lựa chọn {1}"),
    PLACEMENT_CLASS_FULL(2408, HttpStatus.CONFLICT, "Vượt sĩ số tối đa: {0}"),
    PLACEMENT_YEAR_NOT_UPCOMING(2409, HttpStatus.CONFLICT, "Chỉ xếp lớp cho năm học chưa bắt đầu"),
    NO_ACTIVE_YEAR_TO_MOVE_FROM(2410, HttpStatus.CONFLICT, "Không có năm học đang hoạt động để chuyển trẻ"),
    PLACEMENT_NOT_NEXT_YEAR(2411, HttpStatus.CONFLICT, "Chỉ xếp lớp cho năm học kế tiếp {0}"),

    // ========== 30xx HỒ SƠ TRẺ ==========
    CHILD_ACCESS_DENIED(3001, HttpStatus.FORBIDDEN, "Bạn không có quyền xem thông tin trẻ này"),
    CHILD_NOT_FOUND(3002, HttpStatus.NOT_FOUND, "Không tìm thấy trẻ"),
    CHILD_NOT_IN_CLASS(3003, HttpStatus.NOT_FOUND, "Trẻ không thuộc danh sách lớp hiện tại"),
    GUARDIAN_ALREADY_LINKED(3004, HttpStatus.CONFLICT, "Phụ huynh đã được liên kết với trẻ"),
    GUARDIAN_LINK_NOT_FOUND(3005, HttpStatus.NOT_FOUND, "Không tìm thấy liên kết phụ huynh"),
    CHILD_GUARDIAN_MANAGE_DENIED(3006, HttpStatus.FORBIDDEN, "Bạn không có quyền quản lý phụ huynh của trẻ này"),
    GUARDIAN_ACCOUNT_CONFIRMATION_REQUIRED(
            3007,
            HttpStatus.CONFLICT,
            "Số điện thoại đã có tài khoản, hãy tra số và xác nhận đúng người trước khi liên kết"),

    // ========== 40xx ĐIỂM DANH ==========
    CUTOFF_DATE_OUT_OF_YEAR(4001, HttpStatus.BAD_REQUEST, "Ngày áp dụng phải nằm trong năm học {0}"),
    CUTOFF_UNCHANGED(4002, HttpStatus.BAD_REQUEST, "Giờ chốt và ngày áp dụng chưa thay đổi"),
    ATTENDANCE_SUMMARY_ACCESS_DENIED(4003, HttpStatus.FORBIDDEN, "Bạn không có quyền xem tổng hợp điểm danh"),
    ATTENDANCE_YEAR_NOT_ACTIVE(
            4004, HttpStatus.CONFLICT, "Lớp thuộc năm học đã kết thúc hoặc chưa bắt đầu, chỉ xem được"),
    NOT_SCHOOL_DAY(4005, HttpStatus.BAD_REQUEST, "Ngày đã chọn không phải ngày học"),
    ATTENDANCE_TODAY_ONLY(4006, HttpStatus.BAD_REQUEST, "Chỉ điểm danh được cho ngày hôm nay"),
    ATTENDANCE_LOCKED(4007, HttpStatus.CONFLICT, "Điểm danh và báo ăn đã khóa sau {0}"),
    CHILD_LIST_MISMATCH(4008, HttpStatus.BAD_REQUEST, "Danh sách trẻ đã thay đổi, tải lại trang rồi điểm danh lại"),
    UNLOCK_ACCESS_DENIED(4009, HttpStatus.FORBIDDEN, "Bạn không có quyền mở lại điểm danh"),
    ATTENDANCE_UNLOCK_NOT_FOUND(4010, HttpStatus.NOT_FOUND, "Không tìm thấy lần mở lại điểm danh"),
    UNLOCK_YEAR_NOT_ACTIVE(4011, HttpStatus.CONFLICT, "Chỉ mở lại lớp thuộc năm học đang hoạt động"),
    UNLOCK_DATE_OUT_OF_RANGE(4012, HttpStatus.BAD_REQUEST, "Chọn một ngày học từ đầu năm học đến hôm nay"),
    DAY_NOT_LOCKED_YET(4013, HttpStatus.CONFLICT, "Chưa đến giờ khóa, giáo viên vẫn điểm danh được"),
    UNLOCK_DEADLINE_INVALID(
            4014, HttpStatus.BAD_REQUEST, "Chọn hạn mở lại sau thời điểm hiện tại và không quá ngày kết thúc năm học"),
    UNLOCK_ALREADY_ACTIVE(4015, HttpStatus.CONFLICT, "Lớp/ngày này đang có lần mở lại còn hiệu lực"),
    UNLOCK_ALREADY_ENDED(4016, HttpStatus.CONFLICT, "Lần mở lại này đã hết hiệu lực"),
    LEAVE_AFTER_CUTOFF(4017, HttpStatus.CONFLICT, "Đã qua giờ chốt, báo nghỉ cần xác nhận lại số suất"),

    // ========== 50xx SỨC KHỎE ==========
    HEALTH_OVERVIEW_ACCESS_DENIED(5001, HttpStatus.FORBIDDEN, "Bạn không có quyền xem tổng hợp sức khỏe"),
    HEALTH_YEAR_NOT_ACTIVE(5002, HttpStatus.CONFLICT, "Lớp không thuộc năm học đang hoạt động"),
    HEALTH_TODAY_ONLY(5003, HttpStatus.BAD_REQUEST, "Chỉ ghi nhận sức khỏe cho ngày hôm nay"),
    HEALTH_CHILD_NOT_PRESENT(5004, HttpStatus.CONFLICT, "Chỉ ghi nhận cho trẻ đã lưu Có mặt trong ngày hôm nay"),
    HEALTH_NOTE_REQUIRED(5005, HttpStatus.BAD_REQUEST, "Ghi biểu hiện hoặc ghi chú cần lưu ý"),
    HEALTH_ACTIONS_NOT_ALLOWED(5006, HttpStatus.BAD_REQUEST, "Chỉ ghi xử trí khi có dấu hiệu cần lưu ý"),
    HEALTH_REFERRAL_INCOMPLETE(5007, HttpStatus.BAD_REQUEST, "Nhập nơi chuyển trẻ đến và giờ chuyển"),
    HEALTH_CHILD_PICKED_UP(5008, HttpStatus.CONFLICT, "Trẻ đã được trả cho phụ huynh, không ghi thêm được"),

    // ========== 60xx DINH DƯỠNG (BÁO ĂN & SỔ BÁO ĂN) ==========
    MEAL_SESSION_NOT_SERVED(6001, HttpStatus.BAD_REQUEST, "Bữa ăn không thuộc nhóm tuổi của lớp, chọn lại bữa ăn"),
    ABSENT_CHILD_NO_MEAL(6002, HttpStatus.BAD_REQUEST, "Trẻ vắng mặt không thể đăng ký suất ăn"),
    MEAL_COUNT_ACCESS_DENIED(6003, HttpStatus.FORBIDDEN, "Bạn không có quyền với sổ báo ăn này"),
    MEAL_COUNT_NOT_FOUND(6004, HttpStatus.NOT_FOUND, "Không tìm thấy sổ báo ăn"),
    MEAL_COUNT_ALREADY_CONFIRMED(6005, HttpStatus.CONFLICT, "Sổ báo ăn bữa này đã được xác nhận"),
    MEAL_COUNT_UNCHANGED(6006, HttpStatus.BAD_REQUEST, "Số suất chưa thay đổi"),
    MEAL_COUNT_YEAR_READ_ONLY(6007, HttpStatus.CONFLICT, "Năm học không hoạt động, sổ báo ăn chỉ xem"),

    // ========== 70xx KẾ HOẠCH NĂM HỌC (module Tiền) ==========
    YEAR_PLAN_NOT_FOUND(7001, HttpStatus.NOT_FOUND, "Không tìm thấy kế hoạch năm học"),
    YEAR_PLAN_EXISTS(7002, HttpStatus.CONFLICT, "Nhóm tuổi này đã có kế hoạch năm học"),
    YEAR_PLAN_CREATE_FORBIDDEN(7003, HttpStatus.FORBIDDEN, "Bạn không tạo được kế hoạch cho nhóm tuổi này"),
    YEAR_PLAN_ACCESS_DENIED(7004, HttpStatus.FORBIDDEN, "Bạn không có quyền xem kế hoạch giáo dục này"),
    YEAR_PLAN_EDIT_FORBIDDEN(7005, HttpStatus.FORBIDDEN, "Bạn không sửa được kế hoạch này"),
    YEAR_PLAN_LOCKED_BY_OTHER(7006, HttpStatus.CONFLICT, "Đang được {0} sửa"),
    YEAR_PLAN_SUBMIT_FORBIDDEN(7007, HttpStatus.FORBIDDEN, "Chỉ tổ trưởng của nhóm tuổi được gửi duyệt"),
    VICE_PRINCIPAL_ONLY(7008, HttpStatus.FORBIDDEN, "Chỉ Phó Hiệu trưởng được {0} kế hoạch"),
    YEAR_PLAN_INVALID_STATUS(7009, HttpStatus.CONFLICT, "Không thể {0} kế hoạch ở trạng thái hiện tại"),
    RETURN_REASON_REQUIRED(7010, HttpStatus.BAD_REQUEST, "Cần ghi lý do trả lại"),
    THEME_NAME_REQUIRED(7011, HttpStatus.BAD_REQUEST, "Mỗi chủ đề cần tên và tên viết tắt"),
    THEME_ABBR_DUPLICATE(7012, HttpStatus.BAD_REQUEST, "Tên viết tắt của chủ đề không được trùng"),
    THEME_END_BEFORE_START(7013, HttpStatus.BAD_REQUEST, "Ngày kết thúc chủ đề không được trước ngày bắt đầu"),
    PLAN_GOAL_REQUIRED(7014, HttpStatus.BAD_REQUEST, "Kế hoạch cần ít nhất một mục tiêu"),
    THEME_DATE_OUT_OF_YEAR(7015, HttpStatus.BAD_REQUEST, "Ngày của chủ đề, đợt nghỉ phải nằm trong năm học {0} – {1}"),
    THEME_KEY_DUPLICATE(7016, HttpStatus.BAD_REQUEST, "Trùng mã chủ đề: {0}"),
    YEAR_PLAN_REQUEST_REUSED(7017, HttpStatus.CONFLICT, "Yêu cầu tạo đã dùng cho kế hoạch khác"),
    YEAR_PLAN_DELETE_DRAFT_ONLY(7018, HttpStatus.CONFLICT, "Chỉ xóa được kế hoạch đang nháp"),
    EDIT_SESSION_REQUIRED(7019, HttpStatus.BAD_REQUEST, "Thiếu thông tin phiên sửa"),
    EDIT_SESSION_LOST(7020, HttpStatus.CONFLICT, "Đã mất quyền sửa: phiên đã hết hạn hoặc kế hoạch đang mở ở tab khác"),
    YEAR_PLAN_REVISION_STALE(7021, HttpStatus.CONFLICT, "Kế hoạch đã được thay đổi ở nơi khác. Tải lại để đối chiếu"),
    YEAR_PLAN_PENDING_VP_ONLY(7022, HttpStatus.FORBIDDEN, "Kế hoạch đang chờ duyệt: chỉ Phó Hiệu trưởng sửa được"),
    YEAR_PLAN_TITLE_REQUIRED(7023, HttpStatus.BAD_REQUEST, "Cần nhập tên kế hoạch trước khi gửi"),
    BREAK_END_BEFORE_START(7024, HttpStatus.BAD_REQUEST, "Ngày kết thúc đợt nghỉ không được trước ngày bắt đầu"),
    GOAL_CONTENT_REQUIRED(7025, HttpStatus.BAD_REQUEST, "Mỗi mục tiêu cần có nội dung trước khi gửi"),
    EDU_DOMAIN_NOT_FOUND(7026, HttpStatus.BAD_REQUEST, "Không có lĩnh vực giáo dục: {0}"),
    YEAR_PLAN_VERSION_SOURCE_INVALID(
            7027, HttpStatus.CONFLICT, "Chỉ tạo phiên bản mới từ phiên bản đã ban hành mới nhất"),
    YEAR_PLAN_OPEN_VERSION_EXISTS(
            7028, HttpStatus.CONFLICT, "Đã có phiên bản {0} đang soạn. Hãy sửa tiếp phiên bản đó"),
    YEAR_PLAN_VERSION_COMPARE_INVALID(7029, HttpStatus.BAD_REQUEST, "Hai kế hoạch không cùng chuỗi phiên bản"),
    CURRICULUM_GOAL_INVALID(
            7030, HttpStatus.BAD_REQUEST, "Dòng lấy từ danh mục của Bộ không thuộc nhóm tuổi của kế hoạch"),
    THEME_WEEK_ALIGNMENT(7031, HttpStatus.BAD_REQUEST, "Chủ đề {0} phải bắt đầu vào Thứ 2 và kết thúc vào Thứ 6"),
    THEME_DATES_OVERLAP(7032, HttpStatus.BAD_REQUEST, "Chủ đề {0} trùng thời gian với chủ đề {1}"),

    // ========== 71xx TÀI LIỆU LỚP (KẾ HOẠCH CHỦ ĐỀ, GIÁO ÁN) (module Tiền) ==========
    CLASS_DOC_NOT_FOUND(7101, HttpStatus.NOT_FOUND, "Không tìm thấy tài liệu. Lưu bản nháp trước khi gửi"),
    CLASS_DOC_KEY_MISMATCH(7102, HttpStatus.BAD_REQUEST, "Tài liệu không khớp lớp, chủ đề hoặc tuần"),
    CLASS_DOC_TEACHER_ONLY(
            7103, HttpStatus.FORBIDDEN, "Chỉ giáo viên được phân công vào lớp mới sửa hoặc gửi được tài liệu"),
    THEME_NOT_IN_PLAN(7104, HttpStatus.BAD_REQUEST, "Chủ đề không thuộc kế hoạch năm học"),
    WEEK_NOT_IN_THEME(7105, HttpStatus.BAD_REQUEST, "Tuần không phải tuần học của chủ đề"),
    CLASS_DOC_SUBMITTED(7106, HttpStatus.CONFLICT, "Tài liệu đã gửi duyệt, không sửa được"),
    WEEKLY_LESSON_VIA_THEME(7107, HttpStatus.BAD_REQUEST, "Giáo án tuần được gửi và duyệt cùng kế hoạch chủ đề"),
    YEAR_PLAN_CLASS_MISMATCH(7108, HttpStatus.BAD_REQUEST, "Kế hoạch năm học không khớp với lớp"),
    YEAR_PLAN_NOT_ISSUED(7109, HttpStatus.CONFLICT, "Kế hoạch năm học của nhóm tuổi chưa được ban hành"),
    BRANCH_THEMES_NOT_APPROVED(7110, HttpStatus.CONFLICT, "Dự kiến chủ đề nhánh của lớp cần được duyệt trước"),
    THEME_PLAN_NOT_SAVED(7111, HttpStatus.CONFLICT, "Cần lưu kế hoạch giáo dục chủ đề trước"),
    BRANCH_WEEKS_UNNAMED(7112, HttpStatus.CONFLICT, "Còn {0} tuần chưa đặt tên chủ đề nhánh"),
    WEEKLY_LESSONS_MISSING(
            7113, HttpStatus.CONFLICT, "Còn thiếu giáo án {0}. Soạn và lưu đủ giáo án các nhánh rồi mới gửi duyệt"),
    NOT_YOUR_REVIEW_STEP(7114, HttpStatus.FORBIDDEN, "Bạn không {0} được ở bước này"),
    THEME_PLAN_NOT_FOUND(7115, HttpStatus.NOT_FOUND, "Không tìm thấy kế hoạch chủ đề"),
    RESOURCE_THEME_ONLY(7116, HttpStatus.BAD_REQUEST, "Học liệu chỉ thuộc kế hoạch chủ đề"),
    CLASS_DOC_LOCKED(7117, HttpStatus.CONFLICT, "Tài liệu đã khóa chỉnh sửa"),
    LEARNING_RESOURCE_NOT_FOUND(7118, HttpStatus.NOT_FOUND, "Không tìm thấy học liệu"),
    LEARNING_RESOURCE_UNBOUND(7119, HttpStatus.NOT_FOUND, "Học liệu chưa gắn với hoạt động hiện tại"),
    ACTIVITY_CHANGED(7120, HttpStatus.CONFLICT, "Hoạt động đã thay đổi. Lưu và chọn lại hoạt động trước khi gắn tệp"),
    CLASS_DOC_VERSION_FIX_REQUIRED(
            7121, HttpStatus.CONFLICT, "Còn {0} mục đổi theo kế hoạch năm mới chưa sửa (ô đỏ). Sửa hết rồi mới gửi"),
    YEAR_PLAN_SUPERSEDED(
            7122, HttpStatus.CONFLICT, "Kế hoạch năm đã có phiên bản mới. Tạo tài liệu trên phiên bản hiện hành"),
    CLASS_DOC_ADJUSTMENT_INVALID(
            7123, HttpStatus.BAD_REQUEST, "Kế hoạch điều chỉnh chỉ ghi cho ngày Thứ 2–6 của tuần, tối đa 4000 ký tự"),

    // ========== 72xx CẤU HÌNH KẾ HOẠCH GIÁO DỤC (module Tiền) ==========
    EDU_CONFIG_FORBIDDEN(7201, HttpStatus.FORBIDDEN, "Chỉ Hiệu trưởng hoặc Phó Hiệu trưởng sửa được cấu hình"),
    SIGNERS_INVALID(7202, HttpStatus.BAD_REQUEST, "Người ký phải là các vai trò hợp lệ, không trùng nhau"),
    YEAR_PLAN_SIGNER_REQUIRED(7203, HttpStatus.BAD_REQUEST, "Kế hoạch năm học phải có Phó Hiệu trưởng ký"),
    CLASS_DOC_APPROVER_REQUIRED(7204, HttpStatus.BAD_REQUEST, "Mỗi loại tài liệu lớp cần ít nhất một người duyệt"),
    WEEK_ROW_KEY_DUPLICATE(7205, HttpStatus.BAD_REQUEST, "Mã dòng của bảng tuần không được trùng"),
    LESSON_ROW_REQUIRED(7206, HttpStatus.BAD_REQUEST, "Không bỏ được dòng giờ học"),
    DOMAIN_NAME_EXISTS(7207, HttpStatus.CONFLICT, "Tên lĩnh vực {0} đã tồn tại"),

    // ========== 73xx ĐÁNH GIÁ TRẺ ==========
    ASSESSMENT_FUTURE_DATE(7301, HttpStatus.BAD_REQUEST, "Chỉ đánh giá cho hôm nay hoặc ngày đã qua"),
    ASSESSMENT_NOT_SCHOOL_DAY(7302, HttpStatus.BAD_REQUEST, "Ngày đã chọn không phải ngày học"),
    ASSESSMENT_DATE_OUT_OF_YEAR(7303, HttpStatus.BAD_REQUEST, "Chọn ngày trong năm học của lớp, từ {0} đến hôm nay"),
    ASSESSMENT_YEAR_NOT_ACTIVE(
            7304, HttpStatus.CONFLICT, "Lớp thuộc năm học đã kết thúc hoặc chưa bắt đầu, chỉ xem được"),
    ASSESSMENT_DAY_LOCKED(
            7305,
            HttpStatus.CONFLICT,
            "Ngày này đã khóa. Gửi mail cho Phó hiệu trưởng điểm trường hoặc Hiệu trưởng để mở lại"),
    ASSESSMENT_NO_APPROVED_LESSON(7306, HttpStatus.CONFLICT, "Chưa có giáo án duyệt cho ngày này"),
    ASSESSMENT_CHILD_NOT_PRESENT(7307, HttpStatus.CONFLICT, "{0} chưa được điểm danh có mặt nên chưa đánh giá được"),
    ASSESSMENT_ACTIVITY_NOT_IN_LESSON(
            7308, HttpStatus.BAD_REQUEST, "Hoạt động không có trong giáo án đã duyệt của ngày"),
    ASSESSMENT_GOAL_NOT_IN_ACTIVITY(7309, HttpStatus.BAD_REQUEST, "Mục tiêu không thuộc hoạt động này của giáo án"),
    ASSESSMENT_GOAL_TWO_RESULTS(
            7310, HttpStatus.BAD_REQUEST, "Một mục tiêu chỉ chọn một mức Đạt, Cần cố gắng hoặc Chưa đạt"),
    ASSESSMENT_DIAGNOSTIC_TERM(
            7311, HttpStatus.BAD_REQUEST, "Không dùng thuật ngữ chẩn đoán (\"{0}\"). Hãy mô tả hành vi quan sát được"),
    ASSESSMENT_NOTHING_TO_SAVE(7312, HttpStatus.BAD_REQUEST, "Chưa có thay đổi nào để lưu"),
    ASSESSMENT_UNLOCK_YEAR_NOT_ACTIVE(7313, HttpStatus.CONFLICT, "Chỉ mở lại lớp thuộc năm học đang hoạt động"),
    ASSESSMENT_UNLOCK_DATE_OUT_OF_RANGE(7314, HttpStatus.BAD_REQUEST, "Chọn một ngày học từ đầu năm học đến hôm qua"),
    ASSESSMENT_DAY_NOT_LOCKED_YET(7315, HttpStatus.CONFLICT, "Ngày này chưa khóa, giáo viên vẫn đánh giá được"),
    ASSESSMENT_UNLOCK_DEADLINE_INVALID(
            7316, HttpStatus.BAD_REQUEST, "Chọn hạn mở lại sau thời điểm hiện tại và không quá ngày kết thúc năm học"),
    ASSESSMENT_UNLOCK_ALREADY_ACTIVE(7317, HttpStatus.CONFLICT, "Lớp / ngày này đang có lần mở lại còn hiệu lực"),
    ASSESSMENT_UNLOCK_ALREADY_ENDED(7318, HttpStatus.CONFLICT, "Lần mở lại này đã hết hiệu lực"),
    ASSESSMENT_UNLOCK_NOT_FOUND(7319, HttpStatus.NOT_FOUND, "Không tìm thấy lần mở lại ngày đánh giá"),
    ASSESSMENT_UNLOCK_FORBIDDEN(
            7320, HttpStatus.FORBIDDEN, "Chỉ Hiệu trưởng hoặc Phó hiệu trưởng điểm trường của lớp được mở lại"),
    ASSESSMENT_OVERVIEW_FORBIDDEN(
            7321, HttpStatus.FORBIDDEN, "Chỉ Hiệu trưởng và Phó hiệu trưởng xem tổng quan đánh giá"),
    CLASS_COMMENT_TOO_LONG(7322, HttpStatus.BAD_REQUEST, "Nhận xét chung của lớp tối đa 500 ký tự"),
    CLASS_COMMENT_DIAGNOSTIC_TERM(
            7323,
            HttpStatus.BAD_REQUEST,
            "Nhận xét chung của lớp không dùng thuật ngữ chẩn đoán, hãy mô tả điều quan sát được"),
    QUALITY_REPORT_FORBIDDEN(
            7324, HttpStatus.FORBIDDEN, "Chỉ Hiệu trưởng và Phó hiệu trưởng xem báo cáo chất lượng tháng"),
    QUALITY_REPORT_MONTH_OUT_OF_YEAR(
            7325, HttpStatus.BAD_REQUEST, "Chọn tháng trong năm học, từ tháng {0} đến tháng {1}"),
    QUALITY_REPORT_FUTURE_MONTH(7326, HttpStatus.BAD_REQUEST, "Chưa có số liệu của tháng sau tháng hiện tại"),
    TICKET_FORBIDDEN(7327, HttpStatus.FORBIDDEN, "Chỉ giáo viên đang phụ trách lớp được phát phiếu bé ngoan"),
    TICKET_VIEW_FORBIDDEN(7328, HttpStatus.FORBIDDEN, "Bạn không xem được phiếu bé ngoan của lớp này"),
    TICKET_PERIOD_INVALID(
            7329,
            HttpStatus.BAD_REQUEST,
            "Kỳ phát phiếu không hợp lệ: tuần bắt đầu thứ Hai, tháng bắt đầu ngày 1 và nằm trong năm học của lớp"),
    TICKET_PERIOD_NOT_ENDED(7330, HttpStatus.BAD_REQUEST, "Chỉ phát phiếu khi kỳ đã kết thúc (sau ngày {0})"),
    TICKET_NO_CHILDREN(7331, HttpStatus.BAD_REQUEST, "Chọn ít nhất một trẻ để phát phiếu"),
    TICKET_TOO_MANY_CHILDREN(7332, HttpStatus.BAD_REQUEST, "Danh sách trẻ có mã trùng hoặc nhiều hơn sĩ số lớp"),
    TICKET_CHILD_NOT_IN_CLASS(7333, HttpStatus.BAD_REQUEST, "Có trẻ không thuộc lớp trong kỳ này"),
    TICKET_ALREADY_ISSUED(7334, HttpStatus.CONFLICT, "{0} đã được phát phiếu cho kỳ này, hãy tải lại danh sách"),
    TICKET_NOT_ELIGIBLE(7335, HttpStatus.BAD_REQUEST, "{0} chưa đủ điều kiện nhận phiếu {1}"),
    EVALUATION_NOT_FOUND(7336, HttpStatus.NOT_FOUND, "Không tìm thấy đánh giá"),
    EVALUATION_VIEW_FORBIDDEN(7337, HttpStatus.FORBIDDEN, "Bạn không xem được đánh giá của lớp này"),
    EVALUATION_EDIT_FORBIDDEN(
            7338, HttpStatus.FORBIDDEN, "Chỉ giáo viên đang phụ trách lớp được sửa và xác nhận đánh giá"),
    EVALUATION_ALREADY_CONFIRMED(7339, HttpStatus.CONFLICT, "Đánh giá đã được xác nhận, không sửa được nữa"),
    EVALUATION_CONTENT_REQUIRED(7340, HttpStatus.BAD_REQUEST, "Nhập nội dung đánh giá"),
    EVALUATION_CONTENT_TOO_SHORT(7341, HttpStatus.BAD_REQUEST, "Nội dung đánh giá cần ít nhất {0} ký tự"),
    EVALUATION_CONTENT_TOO_LONG(7342, HttpStatus.BAD_REQUEST, "Nội dung đánh giá tối đa {0} ký tự"),
    EVALUATION_DIAGNOSTIC_TERM(
            7343, HttpStatus.BAD_REQUEST, "Không dùng từ mang tính chẩn đoán \"{0}\", hãy mô tả điều cô quan sát được"),
    EVALUATION_PROGRAM_NOT_APPLICABLE(
            7345,
            HttpStatus.BAD_REQUEST,
            "Chỉ đánh dấu hoàn thành chương trình ở đánh giá cuối năm của trẻ lớp 5–6 tuổi"),
    EVALUATION_PROGRAM_REQUIRED(
            7346, HttpStatus.BAD_REQUEST, "Chọn Hoàn thành hoặc Chưa hoàn thành chương trình cho trẻ"),
    PROGRAM_REPORT_FORBIDDEN(
            7347, HttpStatus.FORBIDDEN, "Chỉ Hiệu trưởng và Phó hiệu trưởng xem báo cáo trẻ hoàn thành chương trình"),
    DEVELOPMENT_CHILD_NO_CLASS(7348, HttpStatus.NOT_FOUND, "Trẻ chưa được xếp lớp trong kỳ đã chọn"),
    REWARD_LIST_NOT_FOUND(7349, HttpStatus.NOT_FOUND, "Không tìm thấy danh sách khen thưởng"),
    REWARD_VIEW_FORBIDDEN(7350, HttpStatus.FORBIDDEN, "Bạn không xem được danh sách khen thưởng của lớp này"),
    REWARD_EDIT_FORBIDDEN(
            7351,
            HttpStatus.FORBIDDEN,
            "Chỉ giáo viên đang phụ trách lớp được lập danh sách khen thưởng, khi danh sách là bản nháp hoặc bị trả lại"),
    REWARD_STATUS_CHANGED(7352, HttpStatus.CONFLICT, "Danh sách đã chuyển trạng thái, tải lại trang để xem"),
    REWARD_LIST_EMPTY(7353, HttpStatus.BAD_REQUEST, "Chọn danh hiệu cho ít nhất một trẻ trước khi gửi"),
    REWARD_CHILD_NOT_IN_CLASS(7354, HttpStatus.BAD_REQUEST, "Có trẻ không thuộc lớp, tải lại trang"),
    REWARD_CHILD_DUPLICATE(7355, HttpStatus.BAD_REQUEST, "Một trẻ xuất hiện hai lần trong danh sách"),
    REWARD_TITLE_INVALID(7356, HttpStatus.BAD_REQUEST, "Danh hiệu \"{0}\" không có trong danh sách"),
    REWARD_PROGRAM_TITLE_INVALID(
            7357, HttpStatus.BAD_REQUEST, "{0} chưa được xác nhận hoàn thành chương trình ở đánh giá cuối năm"),
    REWARD_YEAR_END_REQUIRED(7358, HttpStatus.BAD_REQUEST, "{0} chưa có đánh giá cuối năm đã xác nhận"),
    REWARD_NOTE_TOO_LONG(7359, HttpStatus.BAD_REQUEST, "Ghi chú của {0} tối đa {1} ký tự"),
    REWARD_NOTE_DIAGNOSTIC(
            7360,
            HttpStatus.BAD_REQUEST,
            "Ghi chú của {0} có từ mang tính chẩn đoán \"{1}\", hãy mô tả điều cô quan sát được"),
    REWARD_DECISION_INVALID(7361, HttpStatus.BAD_REQUEST, "Quyết định không hợp lệ"),
    REWARD_COMMENT_REQUIRED(7362, HttpStatus.BAD_REQUEST, "Nhập lý do trả lại"),
    REWARD_COMMENT_TOO_LONG(7363, HttpStatus.BAD_REQUEST, "Ý kiến tối đa {0} ký tự"),
    REWARD_REVIEW_FORBIDDEN(
            7364,
            HttpStatus.FORBIDDEN,
            "Chỉ Phó hiệu trưởng điểm trường xem xét, Hiệu trưởng phê duyệt danh sách khen thưởng"),
    EVALUATION_MONTH_INVALID(7344, HttpStatus.BAD_REQUEST, "Chọn tháng trong năm học, từ tháng {0} đến tháng {1}"),

    // ========== 80xx AI & TỆP ==========
    AI_SERVICE_UNAVAILABLE(8001, HttpStatus.SERVICE_UNAVAILABLE, "Trợ lý AI đang không khả dụng, vui lòng thử lại sau"),
    AI_TIMEOUT(8002, HttpStatus.GATEWAY_TIMEOUT, "Trợ lý AI phản hồi quá lâu, vui lòng thử lại"),
    STORAGE_UNAVAILABLE(8003, HttpStatus.SERVICE_UNAVAILABLE, "Không lưu được tệp, vui lòng thử lại sau"),
    UNSUPPORTED_MEDIA_TYPE(8004, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Định dạng tệp không được hỗ trợ"),
    FILE_TOO_LARGE(8005, HttpStatus.PAYLOAD_TOO_LARGE, "Tệp vượt quá dung lượng cho phép"),
    UPLOAD_REQUEST_ID_REQUIRED(8006, HttpStatus.BAD_REQUEST, "Thiếu mã lần tải"),
    FILE_UNREADABLE(8007, HttpStatus.BAD_REQUEST, "Không đọc được tệp"),
    FILE_NOT_FOUND(8008, HttpStatus.NOT_FOUND, "Không tìm thấy tệp"),
    FILE_KEY_INVALID(8009, HttpStatus.BAD_REQUEST, "Mã tệp không hợp lệ"),
    STORAGE_NOT_CONFIGURED(8010, HttpStatus.SERVICE_UNAVAILABLE, "Chưa cấu hình kho lưu tệp, liên hệ quản trị"),
    FILE_NAME_INVALID(8011, HttpStatus.BAD_REQUEST, "Tên tệp không hợp lệ"),
    FILE_EMPTY(8012, HttpStatus.BAD_REQUEST, "Tệp rỗng"),
    FILE_CONTENT_MISMATCH(8013, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Nội dung tệp không khớp định dạng"),

    // ========== 90xx CHUNG & HỆ THỐNG ==========
    INVALID_REQUEST_DATA(9001, HttpStatus.BAD_REQUEST, "Dữ liệu chưa hợp lệ, xem các ô báo đỏ"),
    MALFORMED_REQUEST(9002, HttpStatus.BAD_REQUEST, "Dữ liệu gửi lên không đúng định dạng"),
    REQUIRED_FIELD_MISSING(9003, HttpStatus.BAD_REQUEST, "Thiếu thông tin bắt buộc: {0}"),
    INVALID_DATE_FORMAT(9004, HttpStatus.BAD_REQUEST, "{0} phải có dạng yyyy-MM-dd"),
    INVALID_DATE_RANGE(9005, HttpStatus.BAD_REQUEST, "Chọn khoảng ngày hợp lệ, không chọn ngày trong tương lai"),
    DATE_RANGE_TOO_LONG(9006, HttpStatus.BAD_REQUEST, "Khoảng ngày tối đa {0} ngày"),
    INVALID_VALUE(9007, HttpStatus.BAD_REQUEST, "Giá trị {0} không hợp lệ"),
    METHOD_NOT_ALLOWED(9008, HttpStatus.METHOD_NOT_ALLOWED, "Thao tác không được hỗ trợ"),
    RESOURCE_NOT_FOUND(9009, HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu"),
    INTERNAL_ERROR(9010, HttpStatus.INTERNAL_SERVER_ERROR, "Có lỗi hệ thống, vui lòng thử lại sau"),
    CONFIG_MISSING(9011, HttpStatus.INTERNAL_SERVER_ERROR, "Thiếu dữ liệu cấu hình hệ thống, liên hệ quản trị");

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final Integer errorCode;
    private final HttpStatus httpStatus;
    private final String defaultDesc;

    ApiCode(HttpStatus httpStatus, String defaultDesc) {
        this(null, httpStatus, defaultDesc);
    }

    ApiCode(Integer errorCode, HttpStatus httpStatus, String defaultDesc) {
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.defaultDesc = defaultDesc;
    }

    public int getCode() {
        return httpStatus.value();
    }

    /** Câu lỗi với giá trị thay vào {0}, {1}...; ngày hiện dd/MM/yyyy, số không tách nhóm nghìn. */
    public String format(Object... args) {
        if (args == null || args.length == 0) {
            return defaultDesc;
        }
        Object[] shown = Arrays.stream(args)
                .map(a -> a instanceof LocalDate d ? DISPLAY_DATE.format(d) : a instanceof Number n ? n.toString() : a)
                .toArray();
        return MessageFormat.format(defaultDesc, shown);
    }
}

package com.carenest.utils;

/** Hằng dùng chung và giá trị hệ thống (ADR-0014); hằng private chỉ một class dùng thì giữ tại chỗ. */
public final class Constants {

    private Constants() {}

    /** Web auth gateway (ADR-0013): cookie phiên, thời hạn phiên/token mật khẩu/OTP và ngưỡng gửi OTP. */
    public static final class Auth {
        public static final String SESSION_COOKIE = "CARENEST_SESSION";
        public static final long SETUP_TOKEN_SECONDS = 600;
        public static final long SESSION_SECONDS = 36000;
        public static final long OTP_SECONDS = 300;
        public static final long OTP_RESEND_SECONDS = 60;
        public static final int RATE_LIMIT = 5;

        private Auth() {}
    }

    public static final class Security {
        /** Khóa request attribute giữ CurrentUser đã kiểm ACTIVE, dùng chung interceptor và argument resolver. */
        public static final String CURRENT_USER_ATTRIBUTE = "carenest.currentUser";

        private Security() {}
    }

    /** Mã quyền kế hoạch giáo dục (EDU-08); tạm gán theo role ở EduPlanAccessPolicy cho tới khi có phân quyền động. */
    public static final class EduPermission {
        public static final String GOALS_VIEW = "goals.view";
        public static final String GOALS_EDIT = "goals.edit";
        public static final String GOALS_APPROVE = "goals.approve";
        // EDU-11: Hiệu trưởng, Phó HT sửa cấu hình kế hoạch giáo dục
        public static final String PLAN_CONFIG = "plans.config";

        private EduPermission() {}
    }
}

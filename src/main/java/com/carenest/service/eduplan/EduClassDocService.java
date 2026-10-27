package com.carenest.service.eduplan;

import com.carenest.dto.eduplan.ClassDocActionRequest;
import com.carenest.dto.eduplan.ClassDocsResponse;
import com.carenest.security.CurrentUser;
import tools.jackson.databind.node.ObjectNode;

/** Tài liệu kế hoạch của lớp: chủ đề nhánh, kế hoạch chủ đề, giáo án tuần (EDU-12..18). */
public interface EduClassDocService {

    com.carenest.entity.eduplan.EduClassDoc requireThemeAccess(CurrentUser currentUser, String docKey, boolean write);

    /** Tài liệu, lớp và giáo viên của năm học (null = năm đang hoạt động) trong phạm vi người xem. */
    ClassDocsResponse getClassDocs(CurrentUser currentUser, Long schoolYearId);

    /** Lưu nội dung (tạo mới nếu chưa có); chỉ giáo viên của lớp, khi tài liệu còn Nháp / Bị trả lại. */
    ObjectNode saveClassDoc(CurrentUser currentUser, String kind, String docKey, ObjectNode doc);

    /** Gửi / duyệt / trả lại chủ đề nhánh hoặc hồ sơ chủ đề theo chuỗi người ký đã cấu hình. */
    ObjectNode actOnClassDoc(CurrentUser currentUser, String kind, String docKey, ClassDocActionRequest request);
}

package com.carenest.service.eduplan;

import com.carenest.dto.eduplan.BranchFilePreviewResponse;
import com.carenest.dto.eduplan.ClassCalendarPreviewRequest;
import com.carenest.dto.eduplan.ClassCalendarPreviewResponse;
import com.carenest.dto.eduplan.ClassDocActionRequest;
import com.carenest.dto.eduplan.ClassDocsResponse;
import com.carenest.security.CurrentUser;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.node.ObjectNode;

/** Tài liệu kế hoạch của lớp: chủ đề nhánh, kế hoạch chủ đề, giáo án tuần (EDU-12..18). */
public interface EduClassDocService {

    BranchFilePreviewResponse previewBranchFile(
            CurrentUser currentUser, Long classId, Long yearPlanId, MultipartFile file);

    ClassCalendarPreviewResponse previewClassCalendar(CurrentUser currentUser, ClassCalendarPreviewRequest request);

    com.carenest.entity.eduplan.EduClassDoc requireThemeAccess(CurrentUser currentUser, String docKey, boolean write);

    /** Tài liệu, lớp và giáo viên của năm học (null = năm đang hoạt động) trong phạm vi người xem. */
    ClassDocsResponse getClassDocs(CurrentUser currentUser, Long schoolYearId);

    /** Lưu nội dung (tạo mới nếu chưa có); chỉ giáo viên của lớp, khi tài liệu còn Nháp / Bị trả lại. */
    ObjectNode saveClassDoc(
            CurrentUser currentUser,
            String kind,
            String docKey,
            ObjectNode doc,
            Long expectedVersion,
            Long expectedRootVersion);

    /** Gửi / duyệt / trả lại chủ đề nhánh hoặc hồ sơ chủ đề theo chuỗi người ký đã cấu hình. */
    ObjectNode actOnClassDoc(
            CurrentUser currentUser, String kind, String docKey, ClassDocActionRequest request, Long expectedVersion);

    /** Một bản hồ sơ chủ đề đã ban hành trước đây (nội dung + chuỗi ký), chỉ đọc. */
    ObjectNode getClassDocRelease(CurrentUser currentUser, Long releaseId);

    /** Spec 003 FR-013: nhắc một lần khi gói học kỳ / hồ sơ chủ đề chưa ban hành vào "Gần đến hạn"; trả số gốc đã nhắc. */
    int remindNearDeadlines();

}

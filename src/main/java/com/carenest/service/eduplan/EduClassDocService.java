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

    com.carenest.entity.eduplan.EduClassDoc requireDayAccess(CurrentUser currentUser, String docKey, boolean write);

    /** EDU-96: tiêu chí gửi duyệt của kế hoạch giáo dục chủ đề / kế hoạch giáo dục ngày (đạt / chưa đạt từng dòng), cho ai xem được tài liệu. */
    ObjectNode getClassDocChecks(CurrentUser currentUser, String kind, String docKey);

    /** Trạng thái hiệu lực của giáo án ngày: Chờ duyệt nếu ngày hoặc kế hoạch giáo dục ngày chứa nó Chờ duyệt; ban hành theo hồ sơ. */
    com.carenest.utils.enums.EduClassDocStatus dayStatus(com.carenest.entity.eduplan.EduClassDoc day);

    /** Tài liệu, lớp và giáo viên của năm học (null = năm đang hoạt động) trong phạm vi người xem. */
    ClassDocsResponse getClassDocs(CurrentUser currentUser, Long schoolYearId);

    ClassDocsResponse getClassDocsForReview(CurrentUser currentUser, Long schoolYearId);

    /** Tài liệu (đủ nội dung) của đúng một lớp; classes/staff vẫn đủ phạm vi người xem. Lớp ngoài phạm vi ⇒ rỗng. */
    ClassDocsResponse getClassDocsOfClass(CurrentUser currentUser, Long schoolYearId, boolean reviewView, Long classId);

    /** Danh sách gọn có phân trang (không nội dung soạn); reviewView = hàng chờ duyệt. Cùng luật xem như getClassDocs. */
    com.carenest.dto.common.PageResponse<ObjectNode> getClassDocSummaries(
            CurrentUser currentUser,
            Long schoolYearId,
            boolean reviewView,
            com.carenest.dto.eduplan.ClassDocSummaryFilter filter);

    /** Một tài liệu đầy đủ theo khóa, cùng luật xem và cờ can* như danh sách. */
    ObjectNode getClassDoc(CurrentUser currentUser, String kind, String docKey);

    com.carenest.dto.eduplan.ClassDocContextResponse getClassDocContext(CurrentUser currentUser, Long schoolYearId);

    ClassDocsResponse getOverdueClassDocs(CurrentUser currentUser, Long schoolYearId);

    ObjectNode getOverdueClassDoc(CurrentUser currentUser, Long schoolYearId, String kind, String docKey);

    /** Lưu nội dung (tạo mới nếu chưa có); chỉ giáo viên của lớp, khi tài liệu còn Nháp / Bị trả lại. */
    ObjectNode saveClassDoc(
            CurrentUser currentUser,
            String kind,
            String docKey,
            ObjectNode doc,
            Long expectedVersion,
            Long expectedRootVersion);

    /** Gửi / duyệt / trả lại chủ đề nhánh hoặc kế hoạch giáo dục ngày theo chuỗi người ký đã cấu hình. */
    ObjectNode actOnClassDoc(
            CurrentUser currentUser, String kind, String docKey, ClassDocActionRequest request, Long expectedVersion);

    /** Một bản kế hoạch giáo dục ngày đã ban hành trước đây (nội dung + chuỗi ký), chỉ đọc. */
    ObjectNode getClassDocRelease(CurrentUser currentUser, Long releaseId);

    /** Spec 003 FR-013: nhắc một lần khi kế hoạch giáo dục chủ đề / kế hoạch giáo dục ngày chưa ban hành vào "Gần đến hạn"; trả số gốc đã nhắc. */
    int remindNearDeadlines();
}

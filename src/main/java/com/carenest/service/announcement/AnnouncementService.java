package com.carenest.service.announcement;

import com.carenest.dto.announcement.AnnouncementRequest;
import com.carenest.dto.announcement.AnnouncementResponse;
import com.carenest.dto.announcement.MyAnnouncementResponse;
import com.carenest.dto.announcement.RecipientPreviewResponse;
import com.carenest.dto.announcement.RecipientTargetRequest;
import com.carenest.dto.common.PageResponse;
import com.carenest.security.CurrentUser;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Thông báo của trường do BGH gửi (PAR-05, spec 019 US2): xem trước người nhận, gửi ngay / hẹn giờ, sửa và hủy khi còn
 * hẹn, đính kèm tệp. Người nhận đọc nội dung đầy đủ và tải tệp.
 */
public interface AnnouncementService {

    RecipientPreviewResponse previewRecipients(CurrentUser user, RecipientTargetRequest target);

    AnnouncementResponse createAnnouncement(CurrentUser user, AnnouncementRequest request, List<MultipartFile> files);

    AnnouncementResponse updateAnnouncement(
            CurrentUser user, Long announcementId, AnnouncementRequest request, List<MultipartFile> files);

    AnnouncementResponse cancelAnnouncement(CurrentUser user, Long announcementId);

    PageResponse<AnnouncementResponse> listAnnouncements(CurrentUser user, int page, int size);

    AnnouncementResponse getAnnouncement(CurrentUser user, Long announcementId);

    MyAnnouncementResponse getMyAnnouncement(CurrentUser user, Long announcementId);

    Download downloadAttachment(CurrentUser user, Long attachmentId);

    /** Worker: gửi một thông báo hẹn đã đến giờ (hoặc gửi dở do máy chết). Trả false nếu máy khác đang giữ. */
    boolean dispatchDue(Long announcementId);

    /** Id thông báo cần gửi (hẹn đã đến giờ, hoặc đang gửi dở). */
    List<Long> findDueAnnouncementIds(int limit);

    record Download(Resource resource, String fileName, String contentType) {}
}

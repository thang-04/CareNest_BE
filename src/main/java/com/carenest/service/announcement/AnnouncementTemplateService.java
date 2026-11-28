package com.carenest.service.announcement;

import com.carenest.dto.announcement.AnnouncementTemplateRequest;
import com.carenest.dto.announcement.AnnouncementTemplateResponse;
import com.carenest.security.CurrentUser;
import java.util.List;

/** Mẫu soạn sẵn của BGH (FR-017): HT mọi mẫu; PHT xem mẫu toàn trường + điểm trường mình, sửa/xóa mẫu điểm trường mình. */
public interface AnnouncementTemplateService {

    List<AnnouncementTemplateResponse> listTemplates(CurrentUser user);

    AnnouncementTemplateResponse createTemplate(CurrentUser user, AnnouncementTemplateRequest request);

    AnnouncementTemplateResponse updateTemplate(CurrentUser user, Long templateId, AnnouncementTemplateRequest request);

    void deleteTemplate(CurrentUser user, Long templateId);
}

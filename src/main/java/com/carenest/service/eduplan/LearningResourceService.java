package com.carenest.service.eduplan;

import com.carenest.dto.eduplan.LearningResourceBinding;
import com.carenest.dto.eduplan.LearningResourceResponse;
import com.carenest.security.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface LearningResourceService {
    List<LearningResourceResponse> list(CurrentUser user, String docKey);

    LearningResourceResponse upload(
            CurrentUser user, String docKey, MultipartFile file, LearningResourceBinding binding, UUID requestId);

    LearningResourceResponse bind(CurrentUser user, String docKey, Long id, LearningResourceBinding binding);

    void detach(CurrentUser user, String docKey, Long id);

    Download content(CurrentUser user, String docKey, Long id);

    record Download(Resource resource, String fileName, String contentType) {}
}

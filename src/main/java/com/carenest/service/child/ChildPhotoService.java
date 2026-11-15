package com.carenest.service.child;

import com.carenest.dto.child.ChildViewResponse;
import com.carenest.dto.child.EnrollmentPhotos;
import com.carenest.dto.common.FileUrlResponse;
import com.carenest.dto.common.ImageContent;
import com.carenest.security.CurrentUser;
import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

public interface ChildPhotoService {
    EnrollmentPhotos validatePhotos(int guardianCount, Map<String, List<MultipartFile>> files);

    void attachToEnrollment(Long childId, EnrollmentPhotos photos);

    ImageContent getChildPhoto(CurrentUser actor, Long childId);

    ImageContent getGuardianPhoto(CurrentUser actor, Long childId, Long guardianId);

    /** Liên kết xem có hạn, cùng điều kiện quyền với {@link #getChildPhoto}. */
    FileUrlResponse getChildPhotoUrl(CurrentUser actor, Long childId);

    /** Liên kết xem có hạn, cùng điều kiện quyền với {@link #getGuardianPhoto}. */
    FileUrlResponse getGuardianPhotoUrl(CurrentUser actor, Long childId, Long guardianId);

    ChildViewResponse getProfile(CurrentUser actor, Long childId);
}

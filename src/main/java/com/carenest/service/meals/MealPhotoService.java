package com.carenest.service.meals;

import com.carenest.dto.common.FileUrlResponse;
import com.carenest.dto.meals.MealPhotoResponse;
import com.carenest.dto.meals.MealPhotoWindowResponse;
import com.carenest.dto.meals.MealPhotoWindowsRequest;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.MealSession;
import java.time.LocalDate;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/** Ảnh bữa ăn (NUT-16): HT đặt khung giờ theo bữa, bếp đăng ảnh trong khung giờ, HT/PHT/bếp xem ảnh. */
public interface MealPhotoService {

    List<MealPhotoWindowResponse> getWindows(CurrentUser currentUser);

    /** Chỉ HT; trả toàn bộ khung giờ sau khi ghi. */
    List<MealPhotoWindowResponse> updateWindows(CurrentUser currentUser, MealPhotoWindowsRequest request);

    /** Bếp đăng ảnh bữa của hôm nay, trong khung giờ; đã có ảnh của bữa thì thay. */
    MealPhotoResponse uploadPhoto(CurrentUser currentUser, MealSession session, MultipartFile file);

    List<MealPhotoResponse> getPhotos(CurrentUser currentUser, Long campusId, LocalDate date);

    Content getPhotoContent(CurrentUser currentUser, Long photoId);

    /** Liên kết xem có hạn, cùng điều kiện quyền với {@link #getPhotoContent}. */
    FileUrlResponse getPhotoUrl(CurrentUser currentUser, Long photoId);

    record Content(Resource resource, String contentType) {}
}

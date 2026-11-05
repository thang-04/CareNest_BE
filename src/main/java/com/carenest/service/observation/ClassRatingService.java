package com.carenest.service.observation;

import com.carenest.dto.observation.ClassRatingDtos.ClassRatingResponse;
import com.carenest.dto.observation.ClassRatingDtos.ClassRatingSaveRequest;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.ClassRatingPeriod;

/** Xếp loại nhóm lớp theo học kỳ / cuối năm: HT chấm mọi lớp, PHT chấm lớp điểm trường mình; GV xem lớp mình. */
public interface ClassRatingService {

    /** Lớp trong phạm vi xem (HT, PHT: mọi điểm trường — AUTH-20; GV: lớp mình), kèm canEdit theo từng lớp. */
    ClassRatingResponse list(CurrentUser currentUser, Long schoolYearId, ClassRatingPeriod period, Long campusId);

    /** Lưu nhiều lớp một lần; có lớp ngoài phạm vi chấm hoặc năm đã kết thúc thì từ chối cả lô. */
    ClassRatingResponse save(CurrentUser currentUser, ClassRatingSaveRequest request);
}

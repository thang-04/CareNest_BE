package com.carenest.service.observation;

import com.carenest.dto.observation.DailySheetResponse;
import com.carenest.dto.observation.SaveDailyGeneralRequest;
import com.carenest.dto.observation.SaveDailyResponse;
import com.carenest.dto.observation.SaveDailySkillsRequest;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;

/**
 * Đánh giá trẻ hằng ngày của giáo viên lớp (EDU-19): view Hoạt động chung (nhóm ngày) và view Kiến thức kỹ năng (MT của giáo
 * án đã duyệt). Chỉ trẻ có mặt; ngày khóa hết ngày đó, sửa sau khi khóa cần lần mở lại còn hiệu lực.
 */
public interface DailyAssessmentService {

    DailySheetResponse getDailySheet(CurrentUser currentUser, Long classId, LocalDate date);

    SaveDailyResponse saveGeneral(CurrentUser currentUser, Long classId, SaveDailyGeneralRequest request);

    SaveDailyResponse saveSkills(CurrentUser currentUser, Long classId, SaveDailySkillsRequest request);
}

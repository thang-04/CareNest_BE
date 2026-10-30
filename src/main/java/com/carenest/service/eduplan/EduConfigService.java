package com.carenest.service.eduplan;

import com.carenest.dto.eduplan.EduConfigRequest;
import com.carenest.dto.eduplan.EduConfigResponse;
import com.carenest.security.CurrentUser;

/** Cấu hình và dữ liệu tham chiếu của kế hoạch giáo dục (SD-09, EDU-11). */
public interface EduConfigService {

    EduConfigResponse getEduConfig();

    /** EDU-11: Hiệu trưởng, Phó HT lưu tên lĩnh vực, lĩnh vực thêm, chuỗi người ký, dòng hoạt động tuần. */
    EduConfigResponse updateEduConfig(CurrentUser currentUser, EduConfigRequest request);
}

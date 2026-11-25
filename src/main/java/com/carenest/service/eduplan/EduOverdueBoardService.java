package com.carenest.service.eduplan;

import com.carenest.dto.common.PageResponse;
import com.carenest.security.CurrentUser;
import java.util.List;
import tools.jackson.databind.node.ObjectNode;

/** Màn Kế hoạch quá hạn của HT (spec 005 FR-021a): ghép kế hoạch năm/tổ + hồ sơ lớp, lọc và phân trang ở BE. */
public interface EduOverdueBoardService {

    /**
     * tab: overdue (đang quá hạn, mở được) | extended (đã mở hạn); deadlineKind: send | review; ageGroupId: mã nhóm tuổi
     * (cùng khoảng tháng tuổi); kind: SCHOOL_YEAR | TEAM | semester | dossier. Trường null/rỗng = không lọc.
     */
    record Filter(
            String tab,
            String deadlineKind,
            String ageGroupId,
            String classId,
            String kind,
            Integer semesterNo,
            int page,
            int size) {}

    /** rows: trang dòng (cùng dạng dòng FE overdueRows); overdueCount/extendedCount: số dòng của hai tab, không lọc. */
    record Board(PageResponse<ObjectNode> rows, long overdueCount, long extendedCount, List<Integer> semesters) {}

    Board getOverdueBoard(CurrentUser currentUser, Long schoolYearId, Filter filter);
}

package com.carenest.dto.account;

import com.carenest.utils.enums.CareNestRole;
import java.util.List;

/** EDU-110 BR4.3: các loại hồ sơ có ô tick soạn/duyệt vi phạm luật; rỗng khi hợp lệ. */
public record EduWorkflowIssuesResponse(List<Issue> issues) {

    /** docType: SCHOOL_YEAR | TEAM | SEMESTER_PACKAGE | THEME_PLAN; rule: mã luật BR1.x. */
    public record Issue(String docType, String rule, List<CareNestRole> roles) {}
}

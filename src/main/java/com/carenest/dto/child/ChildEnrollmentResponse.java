package com.carenest.dto.child;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.List;

/** Kết quả tiếp nhận: các trường của hồ sơ trẻ (cùng cấp, như BE giả của FE) + {@code guardianAccounts}. */
public record ChildEnrollmentResponse(
        @JsonUnwrapped ChildViewResponse child, List<GuardianAccountResultResponse> guardianAccounts) {}

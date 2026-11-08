package com.carenest.dto.organization;

/** campusId = null ⇒ gỡ phân công PHT của năm học. Quyền theo chức năng chưa có ở BE (FE còn demo). */
public record VicePrincipalAssignmentRequest(Long campusId) {}

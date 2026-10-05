package com.carenest.entity.account;

/** Trạng thái nghiệp vụ trong CareNest; INACTIVE bị chặn ở mọi request dù token Keycloak còn hạn. */
public enum UserStatus {
    ACTIVE,
    INACTIVE
}

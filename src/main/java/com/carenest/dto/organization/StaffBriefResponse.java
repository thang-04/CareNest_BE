package com.carenest.dto.organization;

/** locked: tài khoản đang bị khóa / ngừng, phân công vẫn còn. */
public record StaffBriefResponse(Long id, String fullName, boolean locked) {}

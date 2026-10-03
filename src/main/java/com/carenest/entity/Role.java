package com.carenest.entity;

/**
 * Roles of a user. A user can hold several roles at the same time (stored in table user_roles).
 * Spring Security sees each one as authority "ROLE_" + name(). Roles do not inherit each other:
 * every endpoint lists the roles it accepts.
 */
public enum Role {
    // Hiệu trưởng: every permission.
    PRINCIPAL,
    // Phó hiệu trưởng: creates staff (except other vice principals) and parent accounts, appoints head teachers.
    VICE_PRINCIPAL,
    // Tổ trưởng giáo viên: always held together with TEACHER.
    HEAD_TEACHER,
    TEACHER,
    // Nhân viên.
    STAFF,
    PARENT
}

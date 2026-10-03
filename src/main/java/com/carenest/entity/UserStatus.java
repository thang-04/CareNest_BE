package com.carenest.entity;

/**
 * Accounts are never deleted (children, pickup reviews and audit data point to them); they are locked instead.
 */
public enum UserStatus {
    ACTIVE,
    // Cannot log in, refresh tokens or call any API; existing tokens stop working at the next request.
    LOCKED
}

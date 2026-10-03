package com.carenest.repository;

import com.carenest.entity.UserStatus;

/**
 * What the access-token check needs from a user, loaded on every request.
 */
public interface UserAuthState {

    UserStatus getStatus();

    int getTokenVersion();
}

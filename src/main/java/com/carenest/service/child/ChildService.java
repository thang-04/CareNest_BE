package com.carenest.service.child;

import com.carenest.dto.child.ChildProfileResponse;
import com.carenest.security.CurrentUser;

public interface ChildService {

    ChildProfileResponse getChildProfile(CurrentUser currentUser, Long childId);
}

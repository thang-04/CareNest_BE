package com.carenest.service.eduplan;

import com.carenest.dto.eduplan.EduDurationPolicyRequest;
import com.carenest.dto.eduplan.EduDurationPolicyResponse;
import com.carenest.security.CurrentUser;

public interface EduDurationPolicyService {
    EduDurationPolicyResponse getDurationPolicy(CurrentUser user);

    EduDurationPolicyResponse saveDurationPolicy(CurrentUser user, EduDurationPolicyRequest request);

    EduDurationPolicyResponse getEffectivePolicy(Long schoolYearId, Long ageGroupId);

    String snapshot();
}

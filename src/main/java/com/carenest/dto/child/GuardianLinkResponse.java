package com.carenest.dto.child;

import com.carenest.utils.enums.GuardianRelationship;
import java.time.Instant;

public record GuardianLinkResponse(Long userId, Long childId, GuardianRelationship relationship, Instant linkedAt) {}

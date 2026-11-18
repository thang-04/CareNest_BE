package com.carenest.dto.announcement;

import java.util.List;

public record ScheduleActivationRequest(List<Long> ids, Boolean active) {}

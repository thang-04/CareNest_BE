package com.carenest.dto.observation;

import java.time.Instant;

public record SaveDailyResponse(int saved, Instant savedAt) {}

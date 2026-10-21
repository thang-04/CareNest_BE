package com.carenest.dto.child;

import java.time.LocalDate;

public record ChildProfileResponse(Long id, String fullName, LocalDate dateOfBirth, Long classroomId) {}

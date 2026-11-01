package com.carenest.dto.child;

import java.time.LocalDate;

/** Trẻ đang học trong một lớp tại một ngày (cho module điểm danh). */
public record ClassChildResponse(Long id, String fullName, LocalDate dateOfBirth) {}

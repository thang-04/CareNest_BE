package com.carenest.dto.child;

import java.time.LocalDate;

/** Thông tin định danh nội bộ; không trả thẳng ra API và không gửi cho AI. */
public record ChildIdentity(Long id, String fullName, LocalDate dateOfBirth) {}

package com.carenest.dto.child;

import com.carenest.utils.enums.Gender;
import java.time.LocalDate;

/** Trẻ rút gọn cho module khác (sức khỏe): mã, họ tên, ngày sinh. */
public record ChildBriefResponse(Long id, String code, String fullName, LocalDate dateOfBirth, Gender gender) {}

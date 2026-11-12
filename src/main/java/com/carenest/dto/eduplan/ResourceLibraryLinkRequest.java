package com.carenest.dto.eduplan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Dán link thẳng vào kho (trang Kho học liệu), không gắn giáo án. */
public record ResourceLibraryLinkRequest(@NotBlank @Size(max = 2000) String url, @Size(max = 255) String title, Long ageGroupId) {}

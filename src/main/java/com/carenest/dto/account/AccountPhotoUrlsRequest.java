package com.carenest.dto.account;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Danh sách tài khoản cần link ảnh đại diện trên một màn (AUTH-27); tối đa 100 người một lần. */
public record AccountPhotoUrlsRequest(@NotEmpty @Size(max = 100) List<@NotNull @Positive Long> userIds) {}

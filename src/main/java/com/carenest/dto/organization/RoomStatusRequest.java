package com.carenest.dto.organization;

import com.carenest.utils.enums.RoomStatus;
import jakarta.validation.constraints.NotNull;

/** Chỉ ACTIVE/INACTIVE; xóa phòng dùng DELETE. */
public record RoomStatusRequest(@NotNull RoomStatus status) {}

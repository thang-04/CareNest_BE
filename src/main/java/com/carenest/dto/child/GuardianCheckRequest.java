package com.carenest.dto.child;

import java.util.List;

/** Kiểm CCCD/SĐT phụ huynh ở bước 1 tiếp nhận (POST để CCCD, SĐT không nằm trên URL). */
public record GuardianCheckRequest(List<GuardianInput> guardians) {}

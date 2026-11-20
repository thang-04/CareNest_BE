package com.carenest.utils.enums;

/** Trạng thái hợp đồng NCC so với hôm nay (NUT-27); EXPIRING = còn ≤ ngưỡng cảnh báo ngày. */
public enum ContractStatus {
    ACTIVE,
    EXPIRING,
    EXPIRED,
    NOT_STARTED,
    NONE
}

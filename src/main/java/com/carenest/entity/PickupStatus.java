package com.carenest.entity;

/**
 * A pickup person may collect the child only when APPROVED by the principal or a vice principal.
 */
public enum PickupStatus {
    PENDING,
    APPROVED,
    REJECTED
}

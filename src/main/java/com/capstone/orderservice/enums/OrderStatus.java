package com.capstone.orderservice.enums;

public enum OrderStatus {
    PENDING,
    CONFIRMED,
    EXPIRED,
    CANCELLED,
    PAYMENT_FAILED;

    public boolean canBeCancelled() {
        return this == PENDING || this == PAYMENT_FAILED;
    }
}

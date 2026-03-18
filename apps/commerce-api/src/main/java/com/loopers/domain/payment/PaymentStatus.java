package com.loopers.domain.payment;

public enum PaymentStatus {
    REQUESTED,
    PROCESSING,
    COMPLETED,
    FAILED,
    TIMEOUT;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}

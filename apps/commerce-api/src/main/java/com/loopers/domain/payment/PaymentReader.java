package com.loopers.domain.payment;

import java.util.List;
import java.util.Optional;

public interface PaymentReader {
    Optional<Payment> findById(Long id);
    Optional<Payment> findByTransactionKey(String transactionKey);
    Optional<Payment> findActiveByOrderId(Long orderId);
    List<Payment> findAllByOrderId(Long orderId);
    List<Payment> findStuckProcessing(int thresholdSeconds);
    List<Payment> findStuckRequested(int thresholdSeconds);
    List<Payment> findTimedOut();
}

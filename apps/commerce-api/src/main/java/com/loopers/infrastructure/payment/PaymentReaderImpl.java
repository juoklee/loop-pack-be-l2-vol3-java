package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentReader;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.QPayment;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class PaymentReaderImpl implements PaymentReader {

    private final PaymentJpaRepository paymentJpaRepository;
    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<Payment> findById(Long id) {
        return paymentJpaRepository.findById(id);
    }

    @Override
    public Optional<Payment> findByIdForUpdate(Long id) {
        return paymentJpaRepository.findByIdForUpdate(id);
    }

    @Override
    public Optional<Payment> findByTransactionKey(String transactionKey) {
        return paymentJpaRepository.findByTransactionKey(transactionKey);
    }

    @Override
    public Optional<Payment> findActiveByOrderId(Long orderId) {
        return paymentJpaRepository.findFirstByOrderIdAndStatusIn(
            orderId, List.of(PaymentStatus.REQUESTED, PaymentStatus.PROCESSING)
        );
    }

    @Override
    public List<Payment> findAllByOrderId(Long orderId) {
        return paymentJpaRepository.findAllByOrderId(orderId);
    }

    @Override
    public List<Payment> findStuckProcessing(int thresholdSeconds, int limit) {
        QPayment payment = QPayment.payment;
        ZonedDateTime threshold = ZonedDateTime.now().minusSeconds(thresholdSeconds);

        return queryFactory.selectFrom(payment)
            .where(
                payment.status.eq(PaymentStatus.PROCESSING),
                payment.updatedAt.before(threshold)
            )
            .orderBy(payment.updatedAt.asc())
            .limit(limit)
            .fetch();
    }

    @Override
    public List<Payment> findStuckRequested(int thresholdSeconds, int limit) {
        QPayment payment = QPayment.payment;
        ZonedDateTime threshold = ZonedDateTime.now().minusSeconds(thresholdSeconds);

        return queryFactory.selectFrom(payment)
            .where(
                payment.status.eq(PaymentStatus.REQUESTED),
                payment.updatedAt.before(threshold)
            )
            .orderBy(payment.updatedAt.asc())
            .limit(limit)
            .fetch();
    }

    @Override
    public List<Payment> findTimedOut(int limit) {
        QPayment payment = QPayment.payment;

        return queryFactory.selectFrom(payment)
            .where(payment.status.eq(PaymentStatus.TIMEOUT))
            .orderBy(payment.updatedAt.asc())
            .limit(limit)
            .fetch();
    }
}

package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payment_order_id", columnList = "order_id"),
    @Index(name = "idx_payment_transaction_key", columnList = "transaction_key", unique = true),
    @Index(name = "idx_payment_status_updated", columnList = "status, updatedAt")
})
public class Payment extends BaseEntity {

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "card_type", nullable = false)
    private String cardType;

    @Column(name = "card_no", nullable = false)
    private String cardNo;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "transaction_key", unique = true)
    private String transactionKey;

    @Column(name = "fail_reason")
    private String failReason;

    protected Payment() {}

    private Payment(Long memberId, Long orderId, String cardType, String cardNo, Long amount) {
        this.memberId = memberId;
        this.orderId = orderId;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.amount = amount;
        this.status = PaymentStatus.REQUESTED;
    }

    public static Payment create(Long memberId, Long orderId, String cardType, String cardNo, Long amount) {
        validateNotNull(memberId, "회원 ID는 필수입니다.");
        validateNotNull(orderId, "주문 ID는 필수입니다.");
        validateNotBlank(cardType, "카드 종류는 필수입니다.");
        validateNotBlank(cardNo, "카드 번호는 필수입니다.");
        validatePositive(amount, "결제 금액은 0보다 커야 합니다.");
        return new Payment(memberId, orderId, cardType, cardNo, amount);
    }

    public void startExecution() {
        if (this.status != PaymentStatus.REQUESTED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "요청 상태의 결제만 실행할 수 있습니다.");
        }
        this.status = PaymentStatus.PROCESSING;
    }

    public void markProcessing(String transactionKey) {
        if (this.status != PaymentStatus.PROCESSING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "처리 중 상태의 결제만 트랜잭션 키를 설정할 수 있습니다.");
        }
        validateNotBlank(transactionKey, "트랜잭션 키는 필수입니다.");
        this.transactionKey = transactionKey;
    }

    public void complete() {
        if (this.status != PaymentStatus.PROCESSING && this.status != PaymentStatus.TIMEOUT) {
            throw new CoreException(ErrorType.BAD_REQUEST, "처리 중이거나 타임아웃 상태의 결제만 완료할 수 있습니다.");
        }
        this.status = PaymentStatus.COMPLETED;
    }

    public void fail(String reason) {
        if (this.status.isTerminal()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 완료된 결제는 실패 처리할 수 없습니다.");
        }
        this.status = PaymentStatus.FAILED;
        this.failReason = reason;
    }

    public void timeout() {
        if (this.status != PaymentStatus.PROCESSING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "처리 중인 결제만 타임아웃 처리할 수 있습니다.");
        }
        this.status = PaymentStatus.TIMEOUT;
        this.failReason = "PG 응답 타임아웃";
    }

    private static void validateNotNull(Object value, String message) {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, message);
        }
    }

    private static void validateNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, message);
        }
    }

    private static void validatePositive(Long value, String message) {
        if (value == null || value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, message);
        }
    }

    public Long getMemberId() { return memberId; }
    public Long getOrderId() { return orderId; }
    public String getCardType() { return cardType; }
    public String getCardNo() { return cardNo; }
    public Long getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public String getTransactionKey() { return transactionKey; }
    public String getFailReason() { return failReason; }
}

package com.loopers.domain.order;

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
@Table(name = "orders", indexes = {
    @Index(name = "idx_order_member_created", columnList = "member_id, createdAt DESC")
})
public class Order extends BaseEntity {

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false)
    private String recipientPhone;

    @Column(name = "zip_code", nullable = false)
    private String zipCode;

    @Column(name = "address1", nullable = false)
    private String address1;

    @Column(name = "address2")
    private String address2;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Column(name = "member_coupon_id")
    private Long memberCouponId;

    @Column(name = "original_amount")
    private Long originalAmount;

    @Column(name = "discount_amount")
    private Long discountAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    protected Order() {}

    private Order(Long memberId, String recipientName, String recipientPhone,
                  String zipCode, String address1, String address2, Long totalAmount,
                  Long memberCouponId, Long originalAmount, Long discountAmount) {
        this.memberId = memberId;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.zipCode = zipCode;
        this.address1 = address1;
        this.address2 = address2;
        this.totalAmount = totalAmount;
        this.memberCouponId = memberCouponId;
        this.originalAmount = originalAmount;
        this.discountAmount = discountAmount;
        this.status = OrderStatus.PENDING_PAYMENT;
    }

    public static Order create(Long memberId, String recipientName, String recipientPhone,
                               String zipCode, String address1, String address2, Long totalAmount) {
        return create(memberId, recipientName, recipientPhone, zipCode, address1, address2,
                      totalAmount, null, null, null);
    }

    public static Order create(Long memberId, String recipientName, String recipientPhone,
                               String zipCode, String address1, String address2, Long totalAmount,
                               Long memberCouponId, Long originalAmount, Long discountAmount) {
        validateNotNull(memberId, "회원 ID는 필수입니다.");
        validateNotBlank(recipientName, "수령인 이름은 필수입니다.");
        validateNotBlank(recipientPhone, "수령인 전화번호는 필수입니다.");
        validateNotBlank(zipCode, "우편번호는 필수입니다.");
        validateNotBlank(address1, "기본주소는 필수입니다.");
        validatePositive(totalAmount, "주문 총액은 0보다 커야 합니다.");
        if (memberCouponId != null) {
            validateNotNull(originalAmount, "쿠폰 적용 시 원래 금액은 필수입니다.");
            validateNotNull(discountAmount, "쿠폰 적용 시 할인 금액은 필수입니다.");
            if (totalAmount != originalAmount - discountAmount) {
                throw new CoreException(ErrorType.BAD_REQUEST, "주문 총액이 원래 금액에서 할인 금액을 뺀 값과 일치하지 않습니다.");
            }
        }
        return new Order(memberId, recipientName, recipientPhone, zipCode, address1, address2,
                         totalAmount, memberCouponId, originalAmount, discountAmount);
    }

    public void completePayment() {
        if (this.status != OrderStatus.PENDING_PAYMENT) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 대기 상태의 주문만 결제 완료할 수 있습니다.");
        }
        this.status = OrderStatus.COMPLETED;
    }

    public void failPayment() {
        if (this.status != OrderStatus.PENDING_PAYMENT) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 대기 상태의 주문만 결제 실패 처리할 수 있습니다.");
        }
        this.status = OrderStatus.PAYMENT_FAILED;
    }

    public void cancel() {
        if (this.status != OrderStatus.COMPLETED && this.status != OrderStatus.PENDING_PAYMENT) {
            throw new CoreException(ErrorType.BAD_REQUEST, "취소할 수 없는 주문 상태입니다.");
        }
        this.status = OrderStatus.CANCELLED;
    }

    public void updateShippingAddress(String recipientName, String recipientPhone,
                                      String zipCode, String address1, String address2) {
        if (this.status != OrderStatus.COMPLETED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "완료된 주문만 배송지를 수정할 수 있습니다.");
        }
        validateNotBlank(recipientName, "수령인 이름은 필수입니다.");
        validateNotBlank(recipientPhone, "수령인 전화번호는 필수입니다.");
        validateNotBlank(zipCode, "우편번호는 필수입니다.");
        validateNotBlank(address1, "기본주소는 필수입니다.");
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.zipCode = zipCode;
        this.address1 = address1;
        this.address2 = address2;
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

    public Long getMemberId() {
        return memberId;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getRecipientPhone() {
        return recipientPhone;
    }

    public String getZipCode() {
        return zipCode;
    }

    public String getAddress1() {
        return address1;
    }

    public String getAddress2() {
        return address2;
    }

    public Long getTotalAmount() {
        return totalAmount;
    }

    public Long getMemberCouponId() {
        return memberCouponId;
    }

    public Long getOriginalAmount() {
        return originalAmount;
    }

    public Long getDiscountAmount() {
        return discountAmount;
    }

    public OrderStatus getStatus() {
        return status;
    }
}

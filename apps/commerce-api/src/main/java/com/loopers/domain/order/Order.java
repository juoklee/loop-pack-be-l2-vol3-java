package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    protected Order() {}

    private Order(Long memberId, String recipientName, String recipientPhone,
                  String zipCode, String address1, String address2, Long totalAmount) {
        this.memberId = memberId;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.zipCode = zipCode;
        this.address1 = address1;
        this.address2 = address2;
        this.totalAmount = totalAmount;
        this.status = OrderStatus.COMPLETED;
    }

    public static Order create(Long memberId, String recipientName, String recipientPhone,
                               String zipCode, String address1, String address2, Long totalAmount) {
        validateNotNull(memberId, "회원 ID는 필수입니다.");
        validateNotBlank(recipientName, "수령인 이름은 필수입니다.");
        validateNotBlank(recipientPhone, "수령인 전화번호는 필수입니다.");
        validateNotBlank(zipCode, "우편번호는 필수입니다.");
        validateNotBlank(address1, "기본주소는 필수입니다.");
        validatePositive(totalAmount, "주문 총액은 0보다 커야 합니다.");
        return new Order(memberId, recipientName, recipientPhone, zipCode, address1, address2, totalAmount);
    }

    public void cancel() {
        if (this.status == OrderStatus.CANCELLED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 취소된 주문입니다.");
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

    public OrderStatus getStatus() {
        return status;
    }
}

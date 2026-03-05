package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "coupon")
public class Coupon extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponType type;

    @Column(nullable = false)
    private Long value;

    @Column(name = "min_order_amount")
    private Long minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    @Column(name = "total_quantity")
    private Integer totalQuantity;

    @Column(name = "issued_quantity", nullable = false)
    private int issuedQuantity;

    protected Coupon() {}

    private Coupon(String name, CouponType type, Long value, Long minOrderAmount, LocalDateTime expiredAt, Integer totalQuantity) {
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
        this.totalQuantity = totalQuantity;
        this.issuedQuantity = 0;
    }

    public static Coupon create(String name, CouponType type, Long value, Long minOrderAmount, LocalDateTime expiredAt) {
        return create(name, type, value, minOrderAmount, expiredAt, null);
    }

    public static Coupon create(String name, CouponType type, Long value, Long minOrderAmount, LocalDateTime expiredAt, Integer totalQuantity) {
        validateNotBlank(name, "쿠폰명은 필수입니다.");
        validateNotNull(type, "쿠폰 타입은 필수입니다.");
        validatePositive(value, "할인 값은 0보다 커야 합니다.");
        validateNotNull(expiredAt, "만료일은 필수입니다.");
        if (type == CouponType.RATE && value > 100) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정률 할인은 100%를 초과할 수 없습니다.");
        }
        return new Coupon(name, type, value, minOrderAmount, expiredAt, totalQuantity);
    }

    public void issueOne() {
        if (totalQuantity != null && issuedQuantity >= totalQuantity) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 수량이 모두 소진되었습니다.");
        }
        this.issuedQuantity++;
    }

    public void updateInfo(String name, Long value, Long minOrderAmount, LocalDateTime expiredAt) {
        validateNotBlank(name, "쿠폰명은 필수입니다.");
        validatePositive(value, "할인 값은 0보다 커야 합니다.");
        validateNotNull(expiredAt, "만료일은 필수입니다.");
        if (this.type == CouponType.RATE && value > 100) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정률 할인은 100%를 초과할 수 없습니다.");
        }
        this.name = name;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public long calculateDiscount(long orderAmount) {
        if (this.type == CouponType.FIXED) {
            return Math.min(this.value, orderAmount);
        }
        return orderAmount * this.value / 100;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiredAt);
    }

    public void validateMinOrderAmount(long orderAmount) {
        if (this.minOrderAmount != null && orderAmount < this.minOrderAmount) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "최소 주문 금액(" + this.minOrderAmount + "원) 이상이어야 합니다.");
        }
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

    public String getName() {
        return name;
    }

    public CouponType getType() {
        return type;
    }

    public Long getValue() {
        return value;
    }

    public Long getMinOrderAmount() {
        return minOrderAmount;
    }

    public LocalDateTime getExpiredAt() {
        return expiredAt;
    }

    public Integer getTotalQuantity() {
        return totalQuantity;
    }

    public int getIssuedQuantity() {
        return issuedQuantity;
    }
}

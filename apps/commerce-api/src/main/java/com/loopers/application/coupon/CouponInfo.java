package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;

import java.time.LocalDateTime;

public record CouponInfo(
    Long id,
    String name,
    String type,
    Long value,
    Long minOrderAmount,
    LocalDateTime expiredAt,
    Integer validDays,
    Integer totalQuantity,
    int issuedQuantity
) {
    public static CouponInfo of(Coupon coupon) {
        return new CouponInfo(
            coupon.getId(),
            coupon.getName(),
            coupon.getType().name(),
            coupon.getValue(),
            coupon.getMinOrderAmount(),
            coupon.getExpiredAt(),
            coupon.getValidDays(),
            coupon.getTotalQuantity(),
            coupon.getIssuedQuantity()
        );
    }
}

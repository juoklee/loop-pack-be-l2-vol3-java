package com.loopers.application.coupon;

import com.loopers.domain.coupon.MemberCoupon;

import java.time.LocalDateTime;

public record MemberCouponInfo(
    Long id,
    Long memberId,
    Long couponId,
    String status,
    LocalDateTime usedAt,
    CouponInfo coupon
) {
    public static MemberCouponInfo of(MemberCoupon memberCoupon, CouponInfo couponInfo) {
        return new MemberCouponInfo(
            memberCoupon.getId(),
            memberCoupon.getMemberId(),
            memberCoupon.getCouponId(),
            memberCoupon.getStatus().name(),
            memberCoupon.getUsedAt(),
            couponInfo
        );
    }
}

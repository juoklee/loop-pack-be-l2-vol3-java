package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;

public record CouponIssueInfo(
    String requestId,
    Long memberId,
    Long couponId,
    String status,
    String failReason,
    Long memberCouponId
) {
    public static CouponIssueInfo of(CouponIssueRequest request) {
        return new CouponIssueInfo(
            request.getRequestId(),
            request.getMemberId(),
            request.getCouponId(),
            request.getStatus().name(),
            request.getFailReason(),
            request.getMemberCouponId()
        );
    }
}

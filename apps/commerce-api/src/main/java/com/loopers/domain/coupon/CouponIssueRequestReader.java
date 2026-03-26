package com.loopers.domain.coupon;

import java.util.Optional;

public interface CouponIssueRequestReader {

    Optional<CouponIssueRequest> findByRequestId(String requestId);

    boolean existsByMemberIdAndCouponIdAndStatusIn(Long memberId, Long couponId,
                                                     java.util.List<CouponIssueRequestStatus> statuses);
}

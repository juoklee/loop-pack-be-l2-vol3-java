package com.loopers.domain.coupon;

import com.loopers.domain.PageResult;

import java.util.Optional;

public interface MemberCouponReader {
    Optional<MemberCoupon> findById(Long id);
    Optional<MemberCoupon> findByMemberIdAndCouponId(Long memberId, Long couponId);
    PageResult<MemberCoupon> findAllByMemberId(Long memberId, int page, int size);
    PageResult<MemberCoupon> findAllByCouponId(Long couponId, int page, int size);
}

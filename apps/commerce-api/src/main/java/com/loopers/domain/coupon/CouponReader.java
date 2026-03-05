package com.loopers.domain.coupon;

import com.loopers.domain.PageResult;

import java.util.Optional;

public interface CouponReader {
    Optional<Coupon> findById(Long id);
    Optional<Coupon> findByIdForUpdate(Long id);
    PageResult<Coupon> findAll(int page, int size);
}

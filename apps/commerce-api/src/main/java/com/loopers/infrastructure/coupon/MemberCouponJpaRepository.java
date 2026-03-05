package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.MemberCoupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberCouponJpaRepository extends JpaRepository<MemberCoupon, Long> {
    Optional<MemberCoupon> findByIdAndDeletedAtIsNull(Long id);
    Optional<MemberCoupon> findByMemberIdAndCouponIdAndDeletedAtIsNull(Long memberId, Long couponId);
    Page<MemberCoupon> findAllByMemberIdAndDeletedAtIsNull(Long memberId, Pageable pageable);
    Page<MemberCoupon> findAllByCouponIdAndDeletedAtIsNull(Long couponId, Pageable pageable);
}

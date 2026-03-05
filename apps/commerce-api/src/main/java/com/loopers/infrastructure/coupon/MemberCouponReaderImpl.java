package com.loopers.infrastructure.coupon;

import com.loopers.domain.PageResult;
import com.loopers.domain.coupon.MemberCoupon;
import com.loopers.domain.coupon.MemberCouponReader;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class MemberCouponReaderImpl implements MemberCouponReader {

    private final MemberCouponJpaRepository memberCouponJpaRepository;

    @Override
    public Optional<MemberCoupon> findById(Long id) {
        return memberCouponJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Optional<MemberCoupon> findByMemberIdAndCouponId(Long memberId, Long couponId) {
        return memberCouponJpaRepository.findByMemberIdAndCouponIdAndDeletedAtIsNull(memberId, couponId);
    }

    @Override
    public PageResult<MemberCoupon> findAllByMemberId(Long memberId, int page, int size) {
        Page<MemberCoupon> result = memberCouponJpaRepository.findAllByMemberIdAndDeletedAtIsNull(
            memberId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return new PageResult<>(
            result.getContent(),
            result.getTotalElements(),
            result.getTotalPages(),
            result.getNumber(),
            result.getSize()
        );
    }

    @Override
    public PageResult<MemberCoupon> findAllByCouponId(Long couponId, int page, int size) {
        Page<MemberCoupon> result = memberCouponJpaRepository.findAllByCouponIdAndDeletedAtIsNull(
            couponId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return new PageResult<>(
            result.getContent(),
            result.getTotalElements(),
            result.getTotalPages(),
            result.getNumber(),
            result.getSize()
        );
    }
}

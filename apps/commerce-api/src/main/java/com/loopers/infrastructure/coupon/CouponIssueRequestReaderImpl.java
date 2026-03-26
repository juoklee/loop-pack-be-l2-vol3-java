package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestReader;
import com.loopers.domain.coupon.CouponIssueRequestStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CouponIssueRequestReaderImpl implements CouponIssueRequestReader {

    private final CouponIssueRequestJpaRepository couponIssueRequestJpaRepository;

    @Override
    public Optional<CouponIssueRequest> findByRequestId(String requestId) {
        return couponIssueRequestJpaRepository.findByRequestId(requestId);
    }

    @Override
    public boolean existsByMemberIdAndCouponIdAndStatusIn(Long memberId, Long couponId,
                                                           List<CouponIssueRequestStatus> statuses) {
        return couponIssueRequestJpaRepository.existsByMemberIdAndCouponIdAndStatusIn(memberId, couponId, statuses);
    }
}

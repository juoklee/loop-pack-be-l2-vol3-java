package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponIssueRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CouponIssueRequestJpaRepository extends JpaRepository<CouponIssueRequest, Long> {

    Optional<CouponIssueRequest> findByRequestId(String requestId);

    boolean existsByMemberIdAndCouponIdAndStatusIn(Long memberId, Long couponId, List<CouponIssueRequestStatus> statuses);
}

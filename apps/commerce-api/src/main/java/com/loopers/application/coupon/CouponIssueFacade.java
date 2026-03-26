package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestReader;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponIssueRequestStatus;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.MemberCoupon;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberService;
import com.loopers.domain.outbox.OutboxEventPublisher;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Component
public class CouponIssueFacade {

    private final MemberService memberService;
    private final CouponService couponService;
    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final CouponIssueRequestReader couponIssueRequestReader;
    private final OutboxEventPublisher outboxEventPublisher;

    @Transactional
    public CouponIssueInfo requestAsyncIssuance(String loginId, Long couponId) {
        Long memberId = getMemberId(loginId);

        boolean alreadyRequested = couponIssueRequestReader.existsByMemberIdAndCouponIdAndStatusIn(
            memberId, couponId,
            List.of(CouponIssueRequestStatus.PENDING, CouponIssueRequestStatus.COMPLETED)
        );
        if (alreadyRequested) {
            throw new CoreException(ErrorType.CONFLICT, "이미 쿠폰 발급 요청이 존재합니다.");
        }

        String requestId = UUID.randomUUID().toString();
        CouponIssueRequest request = CouponIssueRequest.create(requestId, memberId, couponId);
        couponIssueRequestRepository.save(request);

        outboxEventPublisher.publish(
            "COUPON", couponId, "COUPON_ISSUE_REQUESTED",
            "coupon-issue-requests", String.valueOf(couponId),
            Map.of("requestId", requestId, "memberId", memberId, "couponId", couponId)
        );

        return CouponIssueInfo.of(request);
    }

    @Transactional
    public void processIssuance(String requestId) {
        CouponIssueRequest request = couponIssueRequestReader.findByRequestId(requestId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 발급 요청을 찾을 수 없습니다."));

        if (request.isProcessed()) {
            log.info("이미 처리된 쿠폰 발급 요청: requestId={}, status={}", requestId, request.getStatus());
            return;
        }

        try {
            MemberCoupon memberCoupon = couponService.issueCoupon(request.getCouponId(), request.getMemberId());
            request.complete(memberCoupon.getId());
            log.info("쿠폰 발급 성공: requestId={}, memberCouponId={}", requestId, memberCoupon.getId());
        } catch (CoreException e) {
            request.fail(e.getMessage());
            log.warn("쿠폰 발급 실패: requestId={}, reason={}", requestId, e.getMessage());
        } catch (Exception e) {
            log.error("쿠폰 발급 중 예기치 않은 오류: requestId={}, error={}", requestId, e.getMessage(), e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public CouponIssueInfo getIssueRequest(String requestId) {
        CouponIssueRequest request = couponIssueRequestReader.findByRequestId(requestId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 발급 요청을 찾을 수 없습니다."));
        return CouponIssueInfo.of(request);
    }

    private Long getMemberId(String loginId) {
        Member member = memberService.getMemberByLoginId(loginId);
        return member.getId();
    }
}

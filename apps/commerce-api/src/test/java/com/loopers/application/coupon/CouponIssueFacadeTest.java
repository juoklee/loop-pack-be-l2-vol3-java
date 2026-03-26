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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CouponIssueFacadeTest {

    @InjectMocks
    private CouponIssueFacade couponIssueFacade;

    @Mock private MemberService memberService;
    @Mock private CouponService couponService;
    @Mock private CouponIssueRequestRepository couponIssueRequestRepository;
    @Mock private CouponIssueRequestReader couponIssueRequestReader;
    @Mock private OutboxEventPublisher outboxEventPublisher;

    private static final String LOGIN_ID = "testuser";
    private static final Long MEMBER_ID = 1L;
    private static final Long COUPON_ID = 10L;

    private void stubMember() {
        Member member = mock(Member.class);
        given(member.getId()).willReturn(MEMBER_ID);
        given(memberService.getMemberByLoginId(LOGIN_ID)).willReturn(member);
    }

    @Nested
    @DisplayName("requestAsyncIssuance")
    class RequestAsyncIssuance {

        @Test
        @DisplayName("선착순 쿠폰 발급 요청을 생성하고 Outbox에 기록한다")
        void success() {
            // given
            stubMember();
            given(couponIssueRequestReader.existsByMemberIdAndCouponIdAndStatusIn(
                eq(MEMBER_ID), eq(COUPON_ID), any())).willReturn(false);
            given(couponIssueRequestRepository.save(any(CouponIssueRequest.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

            // when
            CouponIssueInfo result = couponIssueFacade.requestAsyncIssuance(LOGIN_ID, COUPON_ID);

            // then
            assertThat(result.status()).isEqualTo("PENDING");
            assertThat(result.requestId()).isNotNull();
            assertThat(result.memberId()).isEqualTo(MEMBER_ID);
            assertThat(result.couponId()).isEqualTo(COUPON_ID);
            verify(outboxEventPublisher).publish(
                eq("COUPON"), eq(COUPON_ID), eq("COUPON_ISSUE_REQUESTED"),
                eq("coupon-issue-requests"), eq(String.valueOf(COUPON_ID)), any());
        }

        @Test
        @DisplayName("이미 PENDING 또는 COMPLETED 요청이 있으면 거부한다")
        void failWhenAlreadyRequested() {
            // given
            stubMember();
            given(couponIssueRequestReader.existsByMemberIdAndCouponIdAndStatusIn(
                eq(MEMBER_ID), eq(COUPON_ID),
                eq(List.of(CouponIssueRequestStatus.PENDING, CouponIssueRequestStatus.COMPLETED))
            )).willReturn(true);

            // when & then
            assertThatThrownBy(() -> couponIssueFacade.requestAsyncIssuance(LOGIN_ID, COUPON_ID))
                .isInstanceOf(CoreException.class);
            verify(couponIssueRequestRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("processIssuance")
    class ProcessIssuance {

        @Test
        @DisplayName("PENDING 요청을 처리하여 쿠폰을 발급하고 COMPLETED로 전이한다")
        void success() {
            // given
            CouponIssueRequest request = CouponIssueRequest.create("req-001", MEMBER_ID, COUPON_ID);
            given(couponIssueRequestReader.findByRequestId("req-001")).willReturn(Optional.of(request));

            MemberCoupon memberCoupon = mock(MemberCoupon.class);
            given(memberCoupon.getId()).willReturn(100L);
            given(couponService.issueCoupon(COUPON_ID, MEMBER_ID)).willReturn(memberCoupon);

            // when
            couponIssueFacade.processIssuance("req-001");

            // then
            assertThat(request.getStatus()).isEqualTo(CouponIssueRequestStatus.COMPLETED);
            assertThat(request.getMemberCouponId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("발급 실패 시 FAILED로 전이하고 사유를 기록한다")
        void failWhenCouponExhausted() {
            // given
            CouponIssueRequest request = CouponIssueRequest.create("req-002", MEMBER_ID, COUPON_ID);
            given(couponIssueRequestReader.findByRequestId("req-002")).willReturn(Optional.of(request));
            given(couponService.issueCoupon(COUPON_ID, MEMBER_ID))
                .willThrow(new CoreException(com.loopers.support.error.ErrorType.BAD_REQUEST, "쿠폰 수량이 모두 소진되었습니다."));

            // when
            couponIssueFacade.processIssuance("req-002");

            // then
            assertThat(request.getStatus()).isEqualTo(CouponIssueRequestStatus.FAILED);
            assertThat(request.getFailReason()).contains("소진");
        }

        @Test
        @DisplayName("이미 처리된 요청은 무시한다 (멱등)")
        void skipAlreadyProcessed() {
            // given
            CouponIssueRequest request = CouponIssueRequest.create("req-003", MEMBER_ID, COUPON_ID);
            request.complete(100L);
            given(couponIssueRequestReader.findByRequestId("req-003")).willReturn(Optional.of(request));

            // when
            couponIssueFacade.processIssuance("req-003");

            // then
            verify(couponService, never()).issueCoupon(anyLong(), anyLong());
        }
    }

    @Nested
    @DisplayName("getIssueRequest")
    class GetIssueRequest {

        @Test
        @DisplayName("requestId로 발급 요청 상태를 조회한다")
        void success() {
            // given
            CouponIssueRequest request = CouponIssueRequest.create("req-004", MEMBER_ID, COUPON_ID);
            given(couponIssueRequestReader.findByRequestId("req-004")).willReturn(Optional.of(request));

            // when
            CouponIssueInfo result = couponIssueFacade.getIssueRequest("req-004");

            // then
            assertThat(result.requestId()).isEqualTo("req-004");
            assertThat(result.status()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("존재하지 않는 requestId는 예외를 던진다")
        void failWhenNotFound() {
            // given
            given(couponIssueRequestReader.findByRequestId("unknown")).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> couponIssueFacade.getIssueRequest("unknown"))
                .isInstanceOf(CoreException.class);
        }
    }
}

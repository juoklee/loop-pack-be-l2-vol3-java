package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestReader;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponIssueRequestStatus;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.member.Gender;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.loopers.domain.member.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CouponAsyncIssueConcurrencyTest {

    @Autowired private CouponIssueFacade couponIssueFacade;
    @Autowired private CouponService couponService;
    @Autowired private CouponIssueRequestRepository couponIssueRequestRepository;
    @Autowired private CouponIssueRequestReader couponIssueRequestReader;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("100장 한정 쿠폰에 200명이 동시 발급 요청하면 정확히 100장만 발급된다")
    void concurrentAsyncIssue_onlyLimitedSucceeds() throws InterruptedException {
        // given
        int totalQuantity = 100;
        int requestCount = 200;

        Coupon coupon = couponService.createCoupon(
            "선착순 쿠폰", CouponType.FIXED, 5000L, null,
            LocalDateTime.now().plusDays(30), totalQuantity
        );
        Long couponId = coupon.getId();

        List<String> requestIds = new ArrayList<>();
        for (int i = 0; i < requestCount; i++) {
            Member member = Member.create("asyncUser" + i, "Test1234!", "테스트유저",
                LocalDate.of(1990, 1, 1), Gender.MALE, "async" + i + "@test.com", null, passwordEncoder);
            Member savedMember = memberRepository.save(member);

            String requestId = UUID.randomUUID().toString();
            CouponIssueRequest request = CouponIssueRequest.create(requestId, savedMember.getId(), couponId);
            couponIssueRequestRepository.save(request);
            requestIds.add(requestId);
        }

        // when — 1차: 200명이 동시에 processIssuance 호출 (동시성 경합 시뮬레이션)
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(requestCount);

        for (String requestId : requestIds) {
            executor.submit(() -> {
                try {
                    couponIssueFacade.processIssuance(requestId);
                } catch (Exception e) {
                    // 락 경합으로 실패한 요청은 PENDING 상태로 남음 (Kafka 재전달 대상)
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // when — 2차: PENDING 상태로 남은 요청을 재처리 (Kafka 재전달 시뮬레이션)
        for (String requestId : requestIds) {
            CouponIssueRequest req = couponIssueRequestReader.findByRequestId(requestId).orElseThrow();
            if (!req.isProcessed()) {
                couponIssueFacade.processIssuance(requestId);
            }
        }

        // then — 정확히 100장만 발급, 나머지 100장은 수량 소진으로 FAILED
        int completedCount = 0;
        int failedCount = 0;
        for (String requestId : requestIds) {
            CouponIssueRequest req = couponIssueRequestReader.findByRequestId(requestId).orElseThrow();
            if (req.getStatus() == CouponIssueRequestStatus.COMPLETED) {
                completedCount++;
            } else if (req.getStatus() == CouponIssueRequestStatus.FAILED) {
                failedCount++;
            }
        }

        assertThat(completedCount).isEqualTo(totalQuantity);
        assertThat(failedCount).isEqualTo(requestCount - totalQuantity);

        Coupon updatedCoupon = couponService.getCoupon(couponId);
        assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(totalQuantity);
    }

    @Test
    @DisplayName("같은 유저가 2번 processIssuance 호출해도 1장만 발급된다 (멱등)")
    void duplicateProcessIssuance_onlyOneIssued() {
        // given
        Coupon coupon = couponService.createCoupon(
            "중복 테스트 쿠폰", CouponType.FIXED, 3000L, null,
            LocalDateTime.now().plusDays(30), 10
        );
        Long couponId = coupon.getId();

        Member member = Member.create("dupUser", "Test1234!", "중복유저",
            LocalDate.of(1990, 1, 1), Gender.MALE, "dup@test.com", null, passwordEncoder);
        Member savedMember = memberRepository.save(member);

        String requestId = UUID.randomUUID().toString();
        CouponIssueRequest request = CouponIssueRequest.create(requestId, savedMember.getId(), couponId);
        couponIssueRequestRepository.save(request);

        // when — 같은 requestId로 2번 호출
        couponIssueFacade.processIssuance(requestId);
        couponIssueFacade.processIssuance(requestId);

        // then — 1장만 발급
        CouponIssueRequest processed = couponIssueRequestReader.findByRequestId(requestId).orElseThrow();
        assertThat(processed.getStatus()).isEqualTo(CouponIssueRequestStatus.COMPLETED);
        assertThat(processed.getMemberCouponId()).isNotNull();

        Coupon updatedCoupon = couponService.getCoupon(couponId);
        assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(1);
    }
}

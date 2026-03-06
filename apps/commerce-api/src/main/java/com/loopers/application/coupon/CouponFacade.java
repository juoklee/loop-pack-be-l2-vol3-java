package com.loopers.application.coupon;

import com.loopers.application.PagedInfo;
import com.loopers.domain.PageResult;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.MemberCoupon;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final MemberService memberService;
    private final CouponService couponService;

    // ── Admin ──

    public CouponInfo createCoupon(String name, String type, Long value, Long minOrderAmount,
                                    LocalDateTime expiredAt, Integer validDays, Integer totalQuantity) {
        CouponType couponType;
        try {
            couponType = CouponType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 쿠폰 타입입니다: " + type);
        }
        Coupon coupon = couponService.createCoupon(name, couponType, value, minOrderAmount, expiredAt, validDays, totalQuantity);
        return CouponInfo.of(coupon);
    }

    public CouponInfo getCoupon(Long couponId) {
        Coupon coupon = couponService.getCoupon(couponId);
        return CouponInfo.of(coupon);
    }

    public PagedInfo<CouponInfo> getCoupons(int page, int size) {
        PageResult<Coupon> result = couponService.getCoupons(page, size);
        List<CouponInfo> infos = result.content().stream().map(CouponInfo::of).toList();
        return new PagedInfo<>(infos, result.totalElements(), result.totalPages(), result.page(), result.size());
    }

    public void updateCoupon(Long couponId, String name, Long value, Long minOrderAmount, LocalDateTime expiredAt, Integer validDays) {
        couponService.updateCoupon(couponId, name, value, minOrderAmount, expiredAt, validDays);
    }

    public void deleteCoupon(Long couponId) {
        couponService.deleteCoupon(couponId);
    }

    public PagedInfo<MemberCouponInfo> getIssuedCoupons(Long couponId, int page, int size) {
        Coupon coupon = couponService.getCoupon(couponId);
        CouponInfo couponInfo = CouponInfo.of(coupon);
        PageResult<MemberCoupon> result = couponService.getIssuedCoupons(couponId, page, size);
        List<MemberCouponInfo> infos = result.content().stream()
            .map(mc -> MemberCouponInfo.of(mc, couponInfo))
            .toList();
        return new PagedInfo<>(infos, result.totalElements(), result.totalPages(), result.page(), result.size());
    }

    // ── 대고객 ──

    public MemberCouponInfo issueCoupon(String loginId, Long couponId) {
        Long memberId = getMemberId(loginId);
        MemberCoupon memberCoupon = couponService.issueCoupon(couponId, memberId);
        Coupon coupon = couponService.getCoupon(couponId);
        return MemberCouponInfo.of(memberCoupon, CouponInfo.of(coupon));
    }

    public PagedInfo<MemberCouponInfo> getMyCoupons(String loginId, int page, int size) {
        Long memberId = getMemberId(loginId);
        PageResult<MemberCoupon> result = couponService.getMemberCoupons(memberId, page, size);

        List<Long> couponIds = result.content().stream().map(MemberCoupon::getCouponId).distinct().toList();
        Map<Long, Coupon> couponMap = couponService.getCoupons(couponIds).stream()
            .collect(Collectors.toMap(Coupon::getId, Function.identity()));

        List<MemberCouponInfo> infos = result.content().stream()
            .map(mc -> MemberCouponInfo.of(mc, CouponInfo.of(couponMap.get(mc.getCouponId()))))
            .toList();
        return new PagedInfo<>(infos, result.totalElements(), result.totalPages(), result.page(), result.size());
    }

    private Long getMemberId(String loginId) {
        Member member = memberService.getMemberByLoginId(loginId);
        return member.getId();
    }
}

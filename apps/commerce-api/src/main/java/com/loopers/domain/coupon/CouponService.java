package com.loopers.domain.coupon;

import com.loopers.domain.PageResult;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponReader couponReader;
    private final MemberCouponRepository memberCouponRepository;
    private final MemberCouponReader memberCouponReader;

    @Transactional
    public Coupon createCoupon(String name, CouponType type, Long value, Long minOrderAmount, LocalDateTime expiredAt) {
        return createCoupon(name, type, value, minOrderAmount, expiredAt, null);
    }

    @Transactional
    public Coupon createCoupon(String name, CouponType type, Long value, Long minOrderAmount, LocalDateTime expiredAt, Integer totalQuantity) {
        return createCoupon(name, type, value, minOrderAmount, expiredAt, null, totalQuantity);
    }

    @Transactional
    public Coupon createCoupon(String name, CouponType type, Long value, Long minOrderAmount,
                                LocalDateTime expiredAt, Integer validDays, Integer totalQuantity) {
        Coupon coupon = Coupon.create(name, type, value, minOrderAmount, expiredAt, validDays, totalQuantity);
        return couponRepository.save(coupon);
    }

    @Transactional(readOnly = true)
    public Coupon getCoupon(Long couponId) {
        return couponReader.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<Coupon> getCoupons(List<Long> couponIds) {
        return couponReader.findAllByIdIn(couponIds);
    }

    @Transactional(readOnly = true)
    public PageResult<Coupon> getCoupons(int page, int size) {
        return couponReader.findAll(page, size);
    }

    @Transactional
    public void updateCoupon(Long couponId, String name, Long value, Long minOrderAmount, LocalDateTime expiredAt, Integer validDays) {
        Coupon coupon = getCoupon(couponId);
        coupon.updateInfo(name, value, minOrderAmount, expiredAt, validDays);
    }

    @Transactional
    public void deleteCoupon(Long couponId) {
        Coupon coupon = getCoupon(couponId);
        coupon.delete();
    }

    @Transactional
    public MemberCoupon issueCoupon(Long couponId, Long memberId) {
        Coupon coupon = couponReader.findByIdForUpdate(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));

        if (coupon.isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰은 발급할 수 없습니다.");
        }

        memberCouponReader.findByMemberIdAndCouponId(memberId, couponId)
            .ifPresent(mc -> {
                throw new CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.");
            });

        coupon.issueOne();
        LocalDateTime memberExpiredAt = coupon.getValidDays() != null
            ? LocalDateTime.now().plusDays(coupon.getValidDays())
            : coupon.getExpiredAt();
        MemberCoupon memberCoupon = MemberCoupon.create(memberId, couponId, memberExpiredAt);
        return memberCouponRepository.save(memberCoupon);
    }

    @Transactional(readOnly = true)
    public MemberCoupon getMemberCoupon(Long memberCouponId) {
        return memberCouponReader.findById(memberCouponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자 쿠폰을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public PageResult<MemberCoupon> getMemberCoupons(Long memberId, int page, int size) {
        return memberCouponReader.findAllByMemberId(memberId, page, size);
    }

    @Transactional(readOnly = true)
    public PageResult<MemberCoupon> getIssuedCoupons(Long couponId, int page, int size) {
        return memberCouponReader.findAllByCouponId(couponId, page, size);
    }

    @Transactional
    public CouponApplyResult useCoupon(Long memberCouponId, Long memberId, long orderAmount) {
        MemberCoupon mc = memberCouponReader.findById(memberCouponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자 쿠폰을 찾을 수 없습니다."));
        mc.validateOwner(memberId);

        Coupon coupon = couponReader.findById(mc.getCouponId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));

        if (mc.isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰은 사용할 수 없습니다.");
        }

        coupon.validateMinOrderAmount(orderAmount);
        long discount = coupon.calculateDiscount(orderAmount);
        mc.use();

        return new CouponApplyResult(coupon.getId(), mc.getId(), discount);
    }

    @Transactional
    public void restoreCoupon(Long memberCouponId) {
        MemberCoupon mc = memberCouponReader.findById(memberCouponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자 쿠폰을 찾을 수 없습니다."));
        mc.restore();
    }

    public record CouponApplyResult(Long couponId, Long memberCouponId, long discountAmount) {}
}

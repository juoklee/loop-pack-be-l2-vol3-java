package com.loopers.interfaces.api.coupon;

import com.loopers.application.PagedInfo;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.MemberCouponInfo;

import java.time.LocalDateTime;
import java.util.List;

public class CouponV1Dto {

    // ── Request ──

    public record CreateCouponRequest(
        String name,
        String type,
        Long value,
        Long minOrderAmount,
        LocalDateTime expiredAt,
        Integer totalQuantity
    ) {}

    public record UpdateCouponRequest(
        String name,
        Long value,
        Long minOrderAmount,
        LocalDateTime expiredAt
    ) {}

    // ── Response ──

    public record CouponResponse(CouponDto coupon) {
        public record CouponDto(
            Long id,
            String name,
            String type,
            Long value,
            Long minOrderAmount,
            LocalDateTime expiredAt,
            Integer totalQuantity,
            int issuedQuantity
        ) {}

        public static CouponResponse from(CouponInfo info) {
            return new CouponResponse(new CouponDto(
                info.id(), info.name(), info.type(),
                info.value(), info.minOrderAmount(), info.expiredAt(),
                info.totalQuantity(), info.issuedQuantity()
            ));
        }
    }

    public record CouponListResponse(
        List<CouponResponse.CouponDto> coupons,
        PageInfo page
    ) {
        public static CouponListResponse from(PagedInfo<CouponInfo> result) {
            var dtos = result.content().stream()
                .map(info -> new CouponResponse.CouponDto(
                    info.id(), info.name(), info.type(),
                    info.value(), info.minOrderAmount(), info.expiredAt(),
                    info.totalQuantity(), info.issuedQuantity()
                ))
                .toList();
            return new CouponListResponse(dtos,
                new PageInfo(result.page(), result.size(), result.totalElements(), result.totalPages()));
        }
    }

    public record MemberCouponResponse(MemberCouponDto memberCoupon) {
        public record MemberCouponDto(
            Long id,
            Long memberId,
            Long couponId,
            String status,
            LocalDateTime usedAt,
            CouponResponse.CouponDto coupon
        ) {}

        public static MemberCouponResponse from(MemberCouponInfo info) {
            return new MemberCouponResponse(new MemberCouponDto(
                info.id(), info.memberId(), info.couponId(),
                info.status(), info.usedAt(),
                new CouponResponse.CouponDto(
                    info.coupon().id(), info.coupon().name(), info.coupon().type(),
                    info.coupon().value(), info.coupon().minOrderAmount(), info.coupon().expiredAt(),
                    info.coupon().totalQuantity(), info.coupon().issuedQuantity()
                )
            ));
        }
    }

    public record MemberCouponListResponse(
        List<MemberCouponResponse.MemberCouponDto> memberCoupons,
        PageInfo page
    ) {
        public static MemberCouponListResponse from(PagedInfo<MemberCouponInfo> result) {
            var dtos = result.content().stream()
                .map(info -> new MemberCouponResponse.MemberCouponDto(
                    info.id(), info.memberId(), info.couponId(),
                    info.status(), info.usedAt(),
                    new CouponResponse.CouponDto(
                        info.coupon().id(), info.coupon().name(), info.coupon().type(),
                        info.coupon().value(), info.coupon().minOrderAmount(), info.coupon().expiredAt(),
                        info.coupon().totalQuantity(), info.coupon().issuedQuantity()
                    )
                ))
                .toList();
            return new MemberCouponListResponse(dtos,
                new PageInfo(result.page(), result.size(), result.totalElements(), result.totalPages()));
        }
    }

    public record PageInfo(int number, int size, long totalElements, int totalPages) {}
}

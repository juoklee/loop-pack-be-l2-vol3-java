package com.loopers.interfaces.api.coupon;

import com.loopers.application.PagedInfo;
import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.MemberCouponInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/coupons")
public class CouponAdminV1Controller {

    private final CouponFacade couponFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CouponV1Dto.CouponResponse> create(
        @RequestBody CouponV1Dto.CreateCouponRequest body
    ) {
        CouponInfo info = couponFacade.createCoupon(
            body.name(), body.type(), body.value(), body.minOrderAmount(), body.expiredAt(), body.validDays(), body.totalQuantity()
        );
        return ApiResponse.success(CouponV1Dto.CouponResponse.from(info));
    }

    @GetMapping
    public ApiResponse<CouponV1Dto.CouponListResponse> getCoupons(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        PagedInfo<CouponInfo> result = couponFacade.getCoupons(page, size);
        return ApiResponse.success(CouponV1Dto.CouponListResponse.from(result));
    }

    @GetMapping("/{couponId}")
    public ApiResponse<CouponV1Dto.CouponResponse> getCoupon(@PathVariable Long couponId) {
        CouponInfo info = couponFacade.getCoupon(couponId);
        return ApiResponse.success(CouponV1Dto.CouponResponse.from(info));
    }

    @PutMapping("/{couponId}")
    public ApiResponse<Void> update(
        @PathVariable Long couponId,
        @RequestBody CouponV1Dto.UpdateCouponRequest body
    ) {
        couponFacade.updateCoupon(couponId, body.name(), body.value(), body.minOrderAmount(), body.expiredAt(), body.validDays());
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{couponId}")
    public ApiResponse<Void> delete(@PathVariable Long couponId) {
        couponFacade.deleteCoupon(couponId);
        return ApiResponse.success(null);
    }

    @GetMapping("/{couponId}/issues")
    public ApiResponse<CouponV1Dto.MemberCouponListResponse> getIssuedCoupons(
        @PathVariable Long couponId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        PagedInfo<MemberCouponInfo> result = couponFacade.getIssuedCoupons(couponId, page, size);
        return ApiResponse.success(CouponV1Dto.MemberCouponListResponse.from(result));
    }
}

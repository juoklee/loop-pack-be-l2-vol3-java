package com.loopers.interfaces.api.coupon;

import com.loopers.application.PagedInfo;
import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.MemberCouponInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class CouponV1Controller {

    private final CouponFacade couponFacade;

    @PostMapping("/api/v1/coupons/{couponId}/issue")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CouponV1Dto.MemberCouponResponse> issueCoupon(
        HttpServletRequest request,
        @PathVariable Long couponId
    ) {
        String loginId = AuthUtils.getAuthenticatedLoginId(request);
        MemberCouponInfo info = couponFacade.issueCoupon(loginId, couponId);
        return ApiResponse.success(CouponV1Dto.MemberCouponResponse.from(info));
    }

    @GetMapping("/api/v1/users/me/coupons")
    public ApiResponse<CouponV1Dto.MemberCouponListResponse> getMyCoupons(
        HttpServletRequest request,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        String loginId = AuthUtils.getAuthenticatedLoginId(request);
        PagedInfo<MemberCouponInfo> result = couponFacade.getMyCoupons(loginId, page, size);
        return ApiResponse.success(CouponV1Dto.MemberCouponListResponse.from(result));
    }
}

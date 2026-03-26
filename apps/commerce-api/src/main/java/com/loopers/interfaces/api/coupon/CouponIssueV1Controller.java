package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponIssueFacade;
import com.loopers.application.coupon.CouponIssueInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class CouponIssueV1Controller {

    private final CouponIssueFacade couponIssueFacade;

    @PostMapping("/api/v1/coupons/{couponId}/issue-async")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<CouponIssueResponse> issueAsync(
        HttpServletRequest request,
        @PathVariable Long couponId
    ) {
        String loginId = AuthUtils.getAuthenticatedLoginId(request);
        CouponIssueInfo info = couponIssueFacade.requestAsyncIssuance(loginId, couponId);
        return ApiResponse.success(CouponIssueResponse.from(info));
    }

    @GetMapping("/api/v1/coupons/issue-requests/{requestId}")
    public ApiResponse<CouponIssueResponse> getIssueRequest(
        @PathVariable String requestId
    ) {
        CouponIssueInfo info = couponIssueFacade.getIssueRequest(requestId);
        return ApiResponse.success(CouponIssueResponse.from(info));
    }

    public record CouponIssueResponse(
        String requestId,
        Long memberId,
        Long couponId,
        String status,
        String failReason,
        Long memberCouponId
    ) {
        public static CouponIssueResponse from(CouponIssueInfo info) {
            return new CouponIssueResponse(
                info.requestId(), info.memberId(), info.couponId(),
                info.status(), info.failReason(), info.memberCouponId()
            );
        }
    }
}

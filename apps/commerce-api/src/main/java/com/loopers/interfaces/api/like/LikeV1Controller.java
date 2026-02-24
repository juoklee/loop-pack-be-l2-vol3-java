package com.loopers.interfaces.api.like;

import com.loopers.application.PagedInfo;
import com.loopers.application.like.BrandLikeInfo;
import com.loopers.application.like.LikeFacade;
import com.loopers.application.like.LikeToggleInfo;
import com.loopers.application.like.ProductLikeInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class LikeV1Controller {

    private final LikeFacade likeFacade;

    @PostMapping("/api/v1/products/{productId}/likes")
    public ApiResponse<LikeV1Dto.ToggleResponse> toggleProductLike(
        HttpServletRequest request,
        @PathVariable Long productId
    ) {
        String loginId = getAuthenticatedLoginId(request);
        LikeToggleInfo info = likeFacade.toggleProductLike(loginId, productId);
        return ApiResponse.success(LikeV1Dto.ToggleResponse.from(info));
    }

    @PostMapping("/api/v1/brands/{brandId}/likes")
    public ApiResponse<LikeV1Dto.ToggleResponse> toggleBrandLike(
        HttpServletRequest request,
        @PathVariable Long brandId
    ) {
        String loginId = getAuthenticatedLoginId(request);
        LikeToggleInfo info = likeFacade.toggleBrandLike(loginId, brandId);
        return ApiResponse.success(LikeV1Dto.ToggleResponse.from(info));
    }

    @GetMapping("/api/v1/members/me/likes/products")
    public ApiResponse<LikeV1Dto.ProductLikeListResponse> getMyLikedProducts(
        HttpServletRequest request,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        String loginId = getAuthenticatedLoginId(request);
        PagedInfo<ProductLikeInfo> result = likeFacade.getMyLikedProducts(
            loginId, pageable.getPageNumber(), pageable.getPageSize()
        );
        return ApiResponse.success(LikeV1Dto.ProductLikeListResponse.from(result));
    }

    @GetMapping("/api/v1/members/me/likes/brands")
    public ApiResponse<LikeV1Dto.BrandLikeListResponse> getMyLikedBrands(
        HttpServletRequest request,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        String loginId = getAuthenticatedLoginId(request);
        PagedInfo<BrandLikeInfo> result = likeFacade.getMyLikedBrands(
            loginId, pageable.getPageNumber(), pageable.getPageSize()
        );
        return ApiResponse.success(LikeV1Dto.BrandLikeListResponse.from(result));
    }

    private String getAuthenticatedLoginId(HttpServletRequest request) {
        Object attribute = request.getAttribute("authenticatedLoginId");
        if (!(attribute instanceof String loginId)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증된 회원 정보가 없습니다.");
        }
        return loginId;
    }
}

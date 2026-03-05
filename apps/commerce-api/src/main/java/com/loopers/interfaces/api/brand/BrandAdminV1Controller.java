package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.brand.BrandInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/brands")
public class BrandAdminV1Controller {

    private final BrandFacade brandFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BrandV1Dto.BrandResponse> register(
        @RequestBody BrandV1Dto.RegisterRequest request
    ) {
        BrandInfo info = brandFacade.register(request.name(), request.description());
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(info));
    }

    @PutMapping("/{brandId}")
    public ApiResponse<Void> update(
        @PathVariable Long brandId,
        @RequestBody BrandV1Dto.UpdateRequest request
    ) {
        brandFacade.updateInfo(brandId, request.name(), request.description());
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{brandId}")
    public ApiResponse<Void> delete(@PathVariable Long brandId) {
        brandFacade.delete(brandId);
        return ApiResponse.success(null);
    }
}

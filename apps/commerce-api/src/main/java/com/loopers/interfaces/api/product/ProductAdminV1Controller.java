package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/products")
public class ProductAdminV1Controller {

    private final ProductFacade productFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductV1Dto.ProductResponse> register(
        @RequestBody ProductV1Dto.RegisterRequest request
    ) {
        ProductInfo info = productFacade.register(
            request.brandId(), request.name(), request.description(),
            request.price(), request.stockQuantity(), request.maxOrderQuantity()
        );
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(info));
    }

    @PutMapping("/{productId}")
    public ApiResponse<Void> update(
        @PathVariable Long productId,
        @RequestBody ProductV1Dto.UpdateRequest request
    ) {
        productFacade.update(productId, request.name(), request.description(),
            request.price(), request.maxOrderQuantity());
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Void> delete(@PathVariable Long productId) {
        productFacade.delete(productId);
        return ApiResponse.success(null);
    }

    @PatchMapping("/{productId}/stock")
    public ApiResponse<Void> updateStock(
        @PathVariable Long productId,
        @RequestBody ProductV1Dto.UpdateStockRequest request
    ) {
        productFacade.updateStock(productId, request.quantity());
        return ApiResponse.success(null);
    }
}

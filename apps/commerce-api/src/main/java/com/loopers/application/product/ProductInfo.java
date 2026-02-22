package com.loopers.application.product;

import com.loopers.application.brand.BrandInfo;
import com.loopers.domain.product.Product;

public record ProductInfo(
    Long id,
    String name,
    String description,
    Long price,
    int stockQuantity,
    int maxOrderQuantity,
    int likeCount,
    BrandInfo brand
) {
    public static ProductInfo of(Product product, BrandInfo brandInfo) {
        return new ProductInfo(
            product.getId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getStockQuantity(),
            product.getMaxOrderQuantity(),
            product.getLikeCount(),
            brandInfo
        );
    }
}

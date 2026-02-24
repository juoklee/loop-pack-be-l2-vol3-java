package com.loopers.application.like;

import com.loopers.application.brand.BrandInfo;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.like.Like;
import com.loopers.domain.product.Product;

import java.time.ZonedDateTime;

public record ProductLikeInfo(
    ProductInfo product,
    ZonedDateTime likedAt
) {
    public static ProductLikeInfo of(Like like, Product product, Brand brand) {
        return new ProductLikeInfo(
            ProductInfo.of(product, BrandInfo.from(brand)),
            like.getCreatedAt()
        );
    }
}

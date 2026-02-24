package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.PagedInfo;

import java.util.List;

public class ProductV1Dto {

    public record RegisterRequest(
        Long brandId,
        String name,
        String description,
        Long price,
        int stockQuantity,
        int maxOrderQuantity
    ) {}

    public record UpdateRequest(
        String name,
        String description,
        Long price,
        int maxOrderQuantity
    ) {}

    public record UpdateStockRequest(
        int quantity
    ) {}

    public record ProductResponse(ProductDto product) {

        public record ProductDto(
            Long id,
            String name,
            String description,
            Long price,
            int stockQuantity,
            int maxOrderQuantity,
            int likeCount,
            BrandDto brand
        ) {}

        public record BrandDto(
            Long id,
            String name,
            String description,
            int likeCount
        ) {}

        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                new ProductDto(
                    info.id(),
                    info.name(),
                    info.description(),
                    info.price(),
                    info.stockQuantity(),
                    info.maxOrderQuantity(),
                    info.likeCount(),
                    new BrandDto(
                        info.brand().id(),
                        info.brand().name(),
                        info.brand().description(),
                        info.brand().likeCount()
                    )
                )
            );
        }
    }

    public record ProductListResponse(
        List<ProductResponse.ProductDto> products,
        PageInfo page
    ) {
        public record PageInfo(int number, int size, long totalElements, int totalPages) {}

        public static ProductListResponse from(PagedInfo<ProductInfo> result) {
            var productDtos = result.content().stream()
                .map(info -> new ProductResponse.ProductDto(
                    info.id(),
                    info.name(),
                    info.description(),
                    info.price(),
                    info.stockQuantity(),
                    info.maxOrderQuantity(),
                    info.likeCount(),
                    new ProductResponse.BrandDto(
                        info.brand().id(),
                        info.brand().name(),
                        info.brand().description(),
                        info.brand().likeCount()
                    )
                ))
                .toList();
            return new ProductListResponse(
                productDtos,
                new PageInfo(result.page(), result.size(), result.totalElements(), result.totalPages())
            );
        }
    }
}

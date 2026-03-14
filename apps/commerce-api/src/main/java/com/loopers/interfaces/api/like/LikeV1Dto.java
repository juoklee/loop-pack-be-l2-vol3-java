package com.loopers.interfaces.api.like;

import com.loopers.application.PagedInfo;
import com.loopers.application.like.BrandLikeInfo;
import com.loopers.application.like.LikeToggleInfo;
import com.loopers.application.like.ProductLikeInfo;

import java.time.ZonedDateTime;
import java.util.List;

public class LikeV1Dto {

    public record ToggleResponse(boolean liked, int likeCount) {
        public static ToggleResponse from(LikeToggleInfo info) {
            return new ToggleResponse(info.liked(), info.likeCount());
        }
    }

    public record ProductLikeListResponse(
        List<ProductLikeDto> products,
        PageInfo page
    ) {
        public record ProductLikeDto(
            ProductDto product,
            ZonedDateTime likedAt
        ) {}

        public record ProductDto(
            Long id,
            String name,
            String description,
            Long price,
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

        public static ProductLikeListResponse from(PagedInfo<ProductLikeInfo> result) {
            var dtos = result.content().stream()
                .map(info -> new ProductLikeDto(
                    new ProductDto(
                        info.product().id(),
                        info.product().name(),
                        info.product().description(),
                        info.product().price(),
                        info.product().maxOrderQuantity(),
                        info.product().likeCount(),
                        new BrandDto(
                            info.product().brand().id(),
                            info.product().brand().name(),
                            info.product().brand().description(),
                            info.product().brand().likeCount()
                        )
                    ),
                    info.likedAt()
                ))
                .toList();
            return new ProductLikeListResponse(
                dtos,
                new PageInfo(result.page(), result.size(), result.totalElements(), result.totalPages())
            );
        }
    }

    public record BrandLikeListResponse(
        List<BrandLikeDto> brands,
        PageInfo page
    ) {
        public record BrandLikeDto(
            BrandDto brand,
            ZonedDateTime likedAt
        ) {}

        public record BrandDto(
            Long id,
            String name,
            String description,
            int likeCount
        ) {}

        public static BrandLikeListResponse from(PagedInfo<BrandLikeInfo> result) {
            var dtos = result.content().stream()
                .map(info -> new BrandLikeDto(
                    new BrandDto(
                        info.brand().id(),
                        info.brand().name(),
                        info.brand().description(),
                        info.brand().likeCount()
                    ),
                    info.likedAt()
                ))
                .toList();
            return new BrandLikeListResponse(
                dtos,
                new PageInfo(result.page(), result.size(), result.totalElements(), result.totalPages())
            );
        }
    }

    public record PageInfo(int number, int size, long totalElements, int totalPages) {}
}

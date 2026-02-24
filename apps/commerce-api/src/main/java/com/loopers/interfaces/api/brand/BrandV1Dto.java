package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;
import com.loopers.application.PagedInfo;

public class BrandV1Dto {

    public record RegisterRequest(
        String name,
        String description
    ) {}

    public record UpdateRequest(
        String name,
        String description
    ) {}

    public record BrandResponse(
        BrandDto brand
    ) {
        public record BrandDto(
            Long id,
            String name,
            String description,
            int likeCount
        ) {}

        public static BrandResponse from(BrandInfo info) {
            return new BrandResponse(
                new BrandDto(
                    info.id(),
                    info.name(),
                    info.description(),
                    info.likeCount()
                )
            );
        }
    }

    public record BrandListResponse(
        java.util.List<BrandResponse.BrandDto> brands,
        PageInfo page
    ) {
        public record PageInfo(
            int number,
            int size,
            long totalElements,
            int totalPages
        ) {}

        public static BrandListResponse from(PagedInfo<BrandInfo> result) {
            var brandDtos = result.content().stream()
                .map(info -> new BrandResponse.BrandDto(
                    info.id(),
                    info.name(),
                    info.description(),
                    info.likeCount()
                ))
                .toList();
            return new BrandListResponse(
                brandDtos,
                new PageInfo(
                    result.page(),
                    result.size(),
                    result.totalElements(),
                    result.totalPages()
                )
            );
        }
    }
}

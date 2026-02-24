package com.loopers.application.like;

import com.loopers.application.brand.BrandInfo;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.like.Like;

import java.time.ZonedDateTime;

public record BrandLikeInfo(
    BrandInfo brand,
    ZonedDateTime likedAt
) {
    public static BrandLikeInfo of(Like like, Brand brand) {
        return new BrandLikeInfo(
            BrandInfo.from(brand),
            like.getCreatedAt()
        );
    }
}

package com.loopers.domain.brand;

public interface BrandRepository {
    Brand save(Brand brand);
    int increaseLikeCount(Long id);
    int decreaseLikeCount(Long id);
    int updateLikeCount(Long id, int likeCount);
}

package com.loopers.domain.brand;

import java.util.List;

public interface BrandRepository {
    Brand save(Brand brand);
    int increaseLikeCount(Long id);
    int decreaseLikeCount(Long id);
    int updateLikeCount(Long id, int likeCount);
    int resetLikeCountsNotIn(List<Long> ids);
    int resetAllLikeCounts();
}

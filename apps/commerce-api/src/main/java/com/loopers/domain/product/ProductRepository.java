package com.loopers.domain.product;

import java.util.List;

public interface ProductRepository {
    Product save(Product product);
    int increaseLikeCount(Long id);
    int decreaseLikeCount(Long id);
    int updateLikeCount(Long id, int likeCount);
    int resetLikeCountsNotIn(List<Long> ids);
    int resetAllLikeCounts();
}

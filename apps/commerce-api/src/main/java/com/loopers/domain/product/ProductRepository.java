package com.loopers.domain.product;

public interface ProductRepository {
    Product save(Product product);
    int increaseLikeCount(Long id);
    int decreaseLikeCount(Long id);
}

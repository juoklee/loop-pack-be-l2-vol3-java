package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;

    @Override
    public Product save(Product product) {
        return productJpaRepository.save(product);
    }

    @Override
    public int increaseLikeCount(Long id) {
        return productJpaRepository.increaseLikeCount(id);
    }

    @Override
    public int decreaseLikeCount(Long id) {
        return productJpaRepository.decreaseLikeCount(id);
    }

    @Override
    public int updateLikeCount(Long id, int likeCount) {
        return productJpaRepository.updateLikeCount(id, likeCount);
    }
}

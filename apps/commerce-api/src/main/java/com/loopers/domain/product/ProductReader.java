package com.loopers.domain.product;

import com.loopers.domain.PageResult;

import java.util.List;
import java.util.Optional;

public interface ProductReader {
    Optional<Product> findById(Long id);
    PageResult<Product> findAll(String keyword, Long brandId, ProductSortType sort, int page, int size);
    List<Product> findAllByIds(List<Long> ids);
    List<Product> findAllByBrandId(Long brandId);
}

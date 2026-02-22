package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductReader {
    Optional<Product> findById(Long id);
    Page<Product> findAll(String keyword, Long brandId, ProductSortType sort, Pageable pageable);
    List<Product> findAllByBrandId(Long brandId);
}

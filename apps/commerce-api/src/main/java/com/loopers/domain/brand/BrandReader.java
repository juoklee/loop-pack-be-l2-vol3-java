package com.loopers.domain.brand;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface BrandReader {
    Optional<Brand> findById(Long id);
    boolean existsByName(String name);
    Page<Brand> findAll(String keyword, Pageable pageable);
}

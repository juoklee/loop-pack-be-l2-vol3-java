package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandReader;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class BrandReaderImpl implements BrandReader {
    private final BrandJpaRepository brandJpaRepository;

    @Override
    public Optional<Brand> findById(Long id) {
        return brandJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public boolean existsByName(String name) {
        return brandJpaRepository.existsByNameAndDeletedAtIsNull(name);
    }

    @Override
    public Page<Brand> findAll(String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return brandJpaRepository.findByDeletedAtIsNull(pageable);
        }
        return brandJpaRepository.findByNameContainingAndDeletedAtIsNull(keyword, pageable);
    }
}

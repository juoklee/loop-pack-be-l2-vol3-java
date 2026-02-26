package com.loopers.infrastructure.brand;

import com.loopers.domain.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandReader;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
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
    public boolean existsById(Long id) {
        return brandJpaRepository.existsByIdAndDeletedAtIsNull(id);
    }

    @Override
    public boolean existsByName(String name) {
        return brandJpaRepository.existsByNameAndDeletedAtIsNull(name);
    }

    @Override
    public List<Brand> findAllByIds(List<Long> ids) {
        return brandJpaRepository.findAllByIdInAndDeletedAtIsNull(ids);
    }

    @Override
    public PageResult<Brand> findAll(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Brand> result;
        if (keyword == null || keyword.isBlank()) {
            result = brandJpaRepository.findByDeletedAtIsNull(pageable);
        } else {
            result = brandJpaRepository.findByNameContainingAndDeletedAtIsNull(keyword, pageable);
        }
        return new PageResult<>(result.getContent(), result.getTotalElements(),
            result.getTotalPages(), result.getNumber(), result.getSize());
    }
}

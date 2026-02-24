package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.Brand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BrandJpaRepository extends JpaRepository<Brand, Long> {
    Optional<Brand> findByIdAndDeletedAtIsNull(Long id);
    boolean existsByIdAndDeletedAtIsNull(Long id);
    boolean existsByNameAndDeletedAtIsNull(String name);
    List<Brand> findAllByIdInAndDeletedAtIsNull(List<Long> ids);
    Page<Brand> findByDeletedAtIsNull(Pageable pageable);
    Page<Brand> findByNameContainingAndDeletedAtIsNull(String keyword, Pageable pageable);
}

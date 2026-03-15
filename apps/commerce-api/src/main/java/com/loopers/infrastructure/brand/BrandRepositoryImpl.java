package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class BrandRepositoryImpl implements BrandRepository {
    private final BrandJpaRepository brandJpaRepository;

    @Override
    public Brand save(Brand brand) {
        return brandJpaRepository.save(brand);
    }

    @Override
    public int increaseLikeCount(Long id) {
        return brandJpaRepository.increaseLikeCount(id);
    }

    @Override
    public int decreaseLikeCount(Long id) {
        return brandJpaRepository.decreaseLikeCount(id);
    }

    @Override
    public int updateLikeCount(Long id, int likeCount) {
        return brandJpaRepository.updateLikeCount(id, likeCount);
    }

    @Override
    public int resetLikeCountsNotIn(List<Long> ids) {
        return brandJpaRepository.resetLikeCountsNotIn(ids);
    }

    @Override
    public int resetAllLikeCounts() {
        return brandJpaRepository.resetAllLikeCounts();
    }
}

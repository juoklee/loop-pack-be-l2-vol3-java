package com.loopers.domain.brand;

import com.loopers.domain.PageResult;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class BrandService {

    private final BrandReader brandReader;
    private final BrandRepository brandRepository;

    @Transactional
    public Brand register(String name, String description) {
        if (brandReader.existsByName(name)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 존재하는 브랜드명입니다.");
        }
        Brand brand = Brand.create(name, description);
        return brandRepository.save(brand);
    }

    @Transactional(readOnly = true)
    public Brand getBrand(Long id) {
        return brandReader.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
    }

    @Transactional
    public void updateInfo(Long id, String name, String description) {
        Brand brand = getBrand(id);
        brand.updateInfo(name, description);
        brandRepository.save(brand);
    }

    @Transactional
    public void delete(Long id) {
        Brand brand = getBrand(id);
        brand.delete();
        brandRepository.save(brand);
    }

    @Transactional
    public void increaseLikeCount(Long id) {
        int updatedCount = brandRepository.increaseLikeCount(id);
        if (updatedCount == 0) {
            throw new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.");
        }
    }

    @Transactional
    public void decreaseLikeCount(Long id) {
        int updatedCount = brandRepository.decreaseLikeCount(id);
        if (updatedCount == 0) {
            throw new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.");
        }
    }

    @Transactional(readOnly = true)
    public List<Brand> getBrandsByIds(List<Long> ids) {
        return brandReader.findAllByIds(ids);
    }

    @Transactional(readOnly = true)
    public PageResult<Brand> getBrands(String keyword, int page, int size) {
        return brandReader.findAll(keyword, page, size);
    }
}

package com.loopers.domain.brand;

import com.loopers.domain.PageResult;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    public void update(Long id, String name, String description) {
        Brand brand = getBrand(id);
        brand.update(name, description);
        brandRepository.save(brand);
    }

    @Transactional
    public void delete(Long id) {
        Brand brand = getBrand(id);
        brand.delete();
        brandRepository.save(brand);
    }

    @Transactional(readOnly = true)
    public PageResult<Brand> getBrands(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Brand> result = brandReader.findAll(keyword, pageable);
        return new PageResult<>(
            result.getContent(),
            result.getTotalElements(),
            result.getTotalPages(),
            result.getNumber(),
            result.getSize()
        );
    }
}

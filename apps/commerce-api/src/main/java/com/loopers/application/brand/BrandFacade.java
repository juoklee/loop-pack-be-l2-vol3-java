package com.loopers.application.brand;

import com.loopers.domain.PageResult;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.brand.Brand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class BrandFacade {

    private final BrandService brandService;

    public BrandInfo register(String name, String description) {
        Brand brand = brandService.register(name, description);
        return BrandInfo.from(brand);
    }

    public BrandInfo getBrand(Long id) {
        Brand brand = brandService.getBrand(id);
        return BrandInfo.from(brand);
    }

    public PageResult<BrandInfo> getBrands(String keyword, int page, int size) {
        PageResult<Brand> result = brandService.getBrands(keyword, page, size);
        return new PageResult<>(
            result.content().stream().map(BrandInfo::from).toList(),
            result.totalElements(),
            result.totalPages(),
            result.page(),
            result.size()
        );
    }

    public void update(Long id, String name, String description) {
        brandService.update(id, name, description);
    }

    public void delete(Long id) {
        // TODO: Phase 2에서 ProductService 추가 후 cascade soft delete 구현
        brandService.delete(id);
    }
}

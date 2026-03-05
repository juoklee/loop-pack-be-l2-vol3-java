package com.loopers.application.brand;

import com.loopers.application.PagedInfo;
import com.loopers.domain.PageResult;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class BrandFacade {

    private final BrandService brandService;
    private final ProductService productService;

    public BrandInfo register(String name, String description) {
        Brand brand = brandService.register(name, description);
        return BrandInfo.from(brand);
    }

    public BrandInfo getBrand(Long id) {
        Brand brand = brandService.getBrand(id);
        return BrandInfo.from(brand);
    }

    public PagedInfo<BrandInfo> getBrands(String keyword, int page, int size) {
        PageResult<Brand> result = brandService.getBrands(keyword, page, size);
        return new PagedInfo<>(
            result.content().stream().map(BrandInfo::from).toList(),
            result.totalElements(),
            result.totalPages(),
            result.page(),
            result.size()
        );
    }

    public void updateInfo(Long id, String name, String description) {
        brandService.updateInfo(id, name, description);
    }

    @Transactional
    public void delete(Long id) {
        brandService.delete(id);
        productService.deleteAllByBrandId(id);
    }
}

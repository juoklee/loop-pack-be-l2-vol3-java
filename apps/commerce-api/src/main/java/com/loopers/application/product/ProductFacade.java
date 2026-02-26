package com.loopers.application.product;

import com.loopers.application.PagedInfo;
import com.loopers.application.brand.BrandInfo;
import com.loopers.domain.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductService productService;
    private final BrandService brandService;

    public ProductInfo register(Long brandId, String name, String description, Long price, int stockQuantity, int maxOrderQuantity) {
        Brand brand = brandService.getBrand(brandId);
        Product product = productService.register(brandId, name, description, price, stockQuantity, maxOrderQuantity);
        return ProductInfo.of(product, BrandInfo.from(brand));
    }

    public ProductInfo getProduct(Long id) {
        Product product = productService.getProduct(id);
        Brand brand = brandService.getBrand(product.getBrandId());
        return ProductInfo.of(product, BrandInfo.from(brand));
    }

    public PagedInfo<ProductInfo> getProducts(String keyword, Long brandId, String sort, int page, int size) {
        ProductSortType sortType = ProductSortType.valueOf(sort);
        PageResult<Product> result = productService.getProducts(keyword, brandId, sortType, page, size);
        List<Long> brandIds = result.content().stream()
            .map(Product::getBrandId).distinct().toList();
        Map<Long, Brand> brandMap = brandService.getBrandsByIds(brandIds).stream()
            .collect(Collectors.toMap(Brand::getId, Function.identity()));
        return new PagedInfo<>(
            result.content().stream().map(product -> {
                Brand brand = brandMap.get(product.getBrandId());
                return ProductInfo.of(product, BrandInfo.from(brand));
            }).toList(),
            result.totalElements(),
            result.totalPages(),
            result.page(),
            result.size()
        );
    }

    public void update(Long id, String name, String description, Long price, int maxOrderQuantity) {
        productService.update(id, name, description, price, maxOrderQuantity);
    }

    public void delete(Long id) {
        productService.delete(id);
    }

    public void updateStock(Long id, int quantity) {
        productService.updateStock(id, quantity);
    }
}

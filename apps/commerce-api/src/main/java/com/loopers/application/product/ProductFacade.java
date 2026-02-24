package com.loopers.application.product;

import com.loopers.application.PagedInfo;
import com.loopers.application.brand.BrandInfo;
import com.loopers.domain.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandReader;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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
    private final BrandReader brandReader;

    public ProductInfo register(Long brandId, String name, String description, Long price, int stockQuantity, int maxOrderQuantity) {
        Brand brand = brandReader.findById(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.BAD_REQUEST, "존재하지 않는 브랜드입니다."));
        Product product = productService.register(brandId, name, description, price, stockQuantity, maxOrderQuantity);
        return ProductInfo.of(product, BrandInfo.from(brand));
    }

    public ProductInfo getProduct(Long id) {
        Product product = productService.getProduct(id);
        Brand brand = brandReader.findById(product.getBrandId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
        return ProductInfo.of(product, BrandInfo.from(brand));
    }

    public PagedInfo<ProductInfo> getProducts(String keyword, Long brandId, String sort, int page, int size) {
        ProductSortType sortType = ProductSortType.valueOf(sort);
        PageResult<Product> result = productService.getProducts(keyword, brandId, sortType, page, size);
        List<Long> brandIds = result.content().stream()
            .map(Product::getBrandId).distinct().toList();
        Map<Long, Brand> brandMap = brandReader.findAllByIds(brandIds).stream()
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

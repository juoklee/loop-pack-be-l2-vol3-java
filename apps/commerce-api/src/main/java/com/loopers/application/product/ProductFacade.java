package com.loopers.application.product;

import com.loopers.application.brand.BrandInfo;
import com.loopers.domain.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductService productService;
    private final BrandService brandService;

    public ProductInfo register(Long brandId, String name, String description, Long price, int stockQuantity, int maxOrderQuantity) {
        Brand brand;
        try {
            brand = brandService.getBrand(brandId);
        } catch (CoreException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "존재하지 않는 브랜드입니다.");
        }
        Product product = productService.register(brandId, name, description, price, stockQuantity, maxOrderQuantity);
        return ProductInfo.of(product, BrandInfo.from(brand));
    }

    public ProductInfo getProduct(Long id) {
        Product product = productService.getProduct(id);
        Brand brand = brandService.getBrand(product.getBrandId());
        return ProductInfo.of(product, BrandInfo.from(brand));
    }

    public PageResult<ProductInfo> getProducts(String keyword, Long brandId, ProductSortType sort, int page, int size) {
        PageResult<Product> result = productService.getProducts(keyword, brandId, sort, page, size);
        return new PageResult<>(
            result.content().stream().map(product -> {
                Brand brand = brandService.getBrand(product.getBrandId());
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

package com.loopers.application.product;

import com.loopers.application.PagedInfo;
import com.loopers.application.brand.BrandInfo;
import com.loopers.domain.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import com.loopers.support.cache.ProductCacheManager;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
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
    private final ProductCacheManager productCacheManager;

    public ProductInfo register(Long brandId, String name, String description, Long price, int stockQuantity, int maxOrderQuantity) {
        Brand brand = brandService.getBrand(brandId);
        Product product = productService.register(brandId, name, description, price, stockQuantity, maxOrderQuantity);
        productCacheManager.evictProductListByBrand(brandId);
        return ProductInfo.of(product, BrandInfo.from(brand));
    }

    @Cacheable(cacheNames = "productDetail", key = "#id")
    public ProductInfo getProduct(Long id) {
        Product product = productService.getProduct(id);
        Brand brand = brandService.getBrand(product.getBrandId());
        return ProductInfo.of(product, BrandInfo.from(brand));
    }

    @Cacheable(cacheNames = "productList",
        key = "(#brandId != null ? #brandId : 'all') + ':' + #sort + ':' + #page + ':' + #size",
        condition = "(#keyword == null || #keyword.isEmpty()) && #page == 0")
    public PagedInfo<ProductInfo> getProducts(String keyword, Long brandId, String sort, int page, int size) {
        ProductSortType sortType;
        try {
            sortType = ProductSortType.valueOf(sort);
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 정렬 타입입니다: " + sort);
        }
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

    public void updateInfo(Long id, String name, String description, Long price, int maxOrderQuantity) {
        Product product = productService.getProduct(id);
        productService.updateInfo(id, name, description, price, maxOrderQuantity);
        productCacheManager.evictProductDetail(id);
        productCacheManager.evictProductListByBrand(product.getBrandId());
    }

    public void delete(Long id) {
        Product product = productService.getProduct(id);
        productService.delete(id);
        productCacheManager.evictProductDetail(id);
        productCacheManager.evictProductListByBrand(product.getBrandId());
    }

    public void updateStock(Long id, int quantity) {
        productService.updateStock(id, quantity);
    }

    public int getStockQuantity(Long id) {
        return productService.getProduct(id).getStockQuantity();
    }
}

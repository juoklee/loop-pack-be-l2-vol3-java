package com.loopers.domain.product;

import com.loopers.domain.PageResult;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductService {

    private final ProductReader productReader;
    private final ProductRepository productRepository;

    @Transactional
    public Product register(Long brandId, String name, String description, Long price, int stockQuantity, int maxOrderQuantity) {
        Product product = Product.create(brandId, name, description, price, stockQuantity, maxOrderQuantity);
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public Product getProduct(Long id) {
        return productReader.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
    }

    @Transactional
    public Product getProductForUpdate(Long id) {
        return productReader.findByIdForUpdate(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
    }

    @Transactional
    public void updateInfo(Long id, String name, String description, Long price, int maxOrderQuantity) {
        Product product = getProduct(id);
        product.updateInfo(name, description, price, maxOrderQuantity);
    }

    @Transactional
    public void delete(Long id) {
        Product product = getProduct(id);
        product.delete();
    }

    @Transactional
    public void updateStock(Long id, int quantity) {
        Product product = getProduct(id);
        product.updateStock(quantity);
    }

    @Transactional
    public void deleteAllByBrandId(Long brandId) {
        List<Product> products = productReader.findAllByBrandId(brandId);
        for (Product product : products) {
            product.delete();
        }
    }

    @Transactional
    public void increaseLikeCount(Long id) {
        productRepository.increaseLikeCount(id);
    }

    @Transactional
    public void decreaseLikeCount(Long id) {
        productRepository.decreaseLikeCount(id);
    }

    @Transactional(readOnly = true)
    public List<Product> getProductsByIds(List<Long> ids) {
        return productReader.findAllByIds(ids);
    }

    @Transactional(readOnly = true)
    public PageResult<Product> getProducts(String keyword, Long brandId, ProductSortType sort, int page, int size) {
        return productReader.findAll(keyword, brandId, sort, page, size);
    }
}

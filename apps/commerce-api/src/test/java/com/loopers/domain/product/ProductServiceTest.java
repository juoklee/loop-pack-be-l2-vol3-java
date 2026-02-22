package com.loopers.domain.product;

import com.loopers.domain.PageResult;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductServiceTest {

    private ProductService productService;
    private FakeProductReader fakeProductReader;
    private FakeProductRepository fakeProductRepository;

    @BeforeEach
    void setUp() {
        fakeProductReader = new FakeProductReader();
        fakeProductRepository = new FakeProductRepository(fakeProductReader);
        productService = new ProductService(fakeProductReader, fakeProductRepository);
    }

    @DisplayName("상품을 등록할 때, ")
    @Nested
    class Register {

        @DisplayName("유효한 정보로 등록하면, 상품이 저장된다.")
        @Test
        void savesProduct_whenAllFieldsAreValid() {
            // Act
            Product product = productService.register(1L, "에어맥스 90", "설명", 139000L, 100, 5);

            // Assert
            assertAll(
                () -> assertThat(product.getBrandId()).isEqualTo(1L),
                () -> assertThat(product.getName()).isEqualTo("에어맥스 90"),
                () -> assertThat(product.getPrice()).isEqualTo(139000L),
                () -> assertThat(product.getStockQuantity()).isEqualTo(100),
                () -> assertThat(product.getMaxOrderQuantity()).isEqualTo(5)
            );
        }
    }

    @DisplayName("상품을 조회할 때, ")
    @Nested
    class GetProduct {

        @DisplayName("존재하는 상품이면, 상품을 반환한다.")
        @Test
        void returnsProduct_whenProductExists() {
            // Arrange
            Product product = productService.register(1L, "에어맥스 90", "설명", 139000L, 100, 5);
            fakeProductReader.addProduct(1L, product);

            // Act
            Product found = productService.getProduct(1L);

            // Assert
            assertThat(found.getName()).isEqualTo("에어맥스 90");
        }

        @DisplayName("존재하지 않는 상품이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductNotExists() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                productService.getProduct(999L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품을 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("유효한 정보로 수정하면, 상품이 수정된다.")
        @Test
        void updatesProduct_whenFieldsAreValid() {
            // Arrange
            Product product = productService.register(1L, "에어맥스 90", "설명", 139000L, 100, 5);
            fakeProductReader.addProduct(1L, product);

            // Act
            productService.update(1L, "에어맥스 95", "수정된 설명", 149000L, 3);

            // Assert
            assertAll(
                () -> assertThat(product.getName()).isEqualTo("에어맥스 95"),
                () -> assertThat(product.getDescription()).isEqualTo("수정된 설명"),
                () -> assertThat(product.getPrice()).isEqualTo(149000L),
                () -> assertThat(product.getMaxOrderQuantity()).isEqualTo(3)
            );
        }

        @DisplayName("존재하지 않는 상품이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductNotExists() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                productService.update(999L, "이름", "설명", 10000L, 5)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품을 삭제할 때, ")
    @Nested
    class Delete {

        @DisplayName("존재하는 상품이면, soft delete 처리된다.")
        @Test
        void deletesProduct_whenProductExists() {
            // Arrange
            Product product = productService.register(1L, "에어맥스 90", "설명", 139000L, 100, 5);
            fakeProductReader.addProduct(1L, product);

            // Act
            productService.delete(1L);

            // Assert
            assertThat(product.getDeletedAt()).isNotNull();
        }

        @DisplayName("존재하지 않는 상품이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductNotExists() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                productService.delete(999L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품 재고를 수정할 때, ")
    @Nested
    class UpdateStock {

        @DisplayName("유효한 수량이면, 재고가 변경된다.")
        @Test
        void updatesStock_whenQuantityIsValid() {
            // Arrange
            Product product = productService.register(1L, "에어맥스 90", "설명", 139000L, 100, 5);
            fakeProductReader.addProduct(1L, product);

            // Act
            productService.updateStock(1L, 200);

            // Assert
            assertThat(product.getStockQuantity()).isEqualTo(200);
        }

        @DisplayName("존재하지 않는 상품이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductNotExists() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                productService.updateStock(999L, 200)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드별 상품을 삭제할 때, ")
    @Nested
    class DeleteAllByBrandId {

        @DisplayName("해당 브랜드의 모든 상품이 soft delete 처리된다.")
        @Test
        void deletesAllProducts_whenBrandIdMatches() {
            // Arrange
            Product product1 = productService.register(1L, "상품1", "설명", 10000L, 10, 5);
            Product product2 = productService.register(1L, "상품2", "설명", 20000L, 20, 5);
            fakeProductReader.addProductsByBrandId(1L, List.of(product1, product2));

            // Act
            productService.deleteAllByBrandId(1L);

            // Assert
            assertAll(
                () -> assertThat(product1.getDeletedAt()).isNotNull(),
                () -> assertThat(product2.getDeletedAt()).isNotNull()
            );
        }

        @DisplayName("해당 브랜드의 상품이 없으면, 아무 일도 일어나지 않는다.")
        @Test
        void doesNothing_whenNoProductsForBrand() {
            // Act & Assert (no exception)
            productService.deleteAllByBrandId(999L);
        }
    }

    @DisplayName("상품 목록을 조회할 때, ")
    @Nested
    class GetProducts {

        @DisplayName("상품이 존재하면, 페이징된 결과를 반환한다.")
        @Test
        void returnsPagedResult_whenProductsExist() {
            // Arrange
            Product product1 = productService.register(1L, "상품1", "설명", 10000L, 10, 5);
            Product product2 = productService.register(1L, "상품2", "설명", 20000L, 20, 5);
            fakeProductReader.setAllProducts(List.of(product1, product2));

            // Act
            PageResult<Product> result = productService.getProducts(null, null, ProductSortType.LATEST, 0, 20);

            // Assert
            assertAll(
                () -> assertThat(result.content()).hasSize(2),
                () -> assertThat(result.page()).isZero(),
                () -> assertThat(result.size()).isEqualTo(20)
            );
        }
    }

    // Fake 구현체
    static class FakeProductReader implements ProductReader {
        private final Map<Long, Product> products = new HashMap<>();
        private List<Product> allProducts = List.of();
        private final Map<Long, List<Product>> productsByBrandId = new HashMap<>();

        void addProduct(Long id, Product product) {
            products.put(id, product);
        }

        void setAllProducts(List<Product> products) {
            this.allProducts = products;
        }

        void addProductsByBrandId(Long brandId, List<Product> products) {
            this.productsByBrandId.put(brandId, products);
        }

        @Override
        public Optional<Product> findById(Long id) {
            return Optional.ofNullable(products.get(id));
        }

        @Override
        public Page<Product> findAll(String keyword, Long brandId, ProductSortType sort, Pageable pageable) {
            return new PageImpl<>(allProducts, pageable, allProducts.size());
        }

        @Override
        public List<Product> findAllByBrandId(Long brandId) {
            return productsByBrandId.getOrDefault(brandId, List.of());
        }
    }

    static class FakeProductRepository implements ProductRepository {
        private final Map<Long, Product> products = new HashMap<>();
        private final FakeProductReader fakeProductReader;
        private long idSequence = 1L;

        FakeProductRepository(FakeProductReader fakeProductReader) {
            this.fakeProductReader = fakeProductReader;
        }

        @Override
        public Product save(Product product) {
            long id = idSequence++;
            products.put(id, product);
            fakeProductReader.addProduct(id, product);
            return product;
        }
    }
}

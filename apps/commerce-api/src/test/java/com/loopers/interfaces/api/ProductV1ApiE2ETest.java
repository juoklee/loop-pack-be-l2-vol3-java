package com.loopers.interfaces.api;

import com.loopers.interfaces.api.brand.BrandV1Dto;
import com.loopers.interfaces.api.product.ProductV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cache.CacheManager;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest {

    private static final String PRODUCT_PUBLIC = "/api/v1/products";
    private static final String PRODUCT_ADMIN = "/api-admin/v1/products";
    private static final String BRAND_ADMIN = "/api-admin/v1/brands";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final CacheManager cacheManager;

    @Autowired
    public ProductV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        DatabaseCleanUp databaseCleanUp,
        CacheManager cacheManager
    ) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.cacheManager = cacheManager;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @DisplayName("POST /api-admin/v1/products (상품 등록)")
    @Nested
    class Register {

        @DisplayName("유효한 정보로 등록하면, 201 Created 응답과 브랜드 정보를 받는다.")
        @Test
        void returnsCreated_whenValidRequest() {
            // Arrange
            Long brandId = registerBrand("Nike", "Just Do It");
            var request = new ProductV1Dto.RegisterRequest(brandId, "에어맥스 90", "클래식 운동화", 139000L, 100, 5);

            // Act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                PRODUCT_ADMIN,
                HttpMethod.POST,
                adminEntity(request),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().data().product().name()).isEqualTo("에어맥스 90"),
                () -> assertThat(response.getBody().data().product().price()).isEqualTo(139000L),
                () -> assertThat(response.getBody().data().product().maxOrderQuantity()).isEqualTo(5),
                () -> assertThat(response.getBody().data().product().likeCount()).isZero(),
                () -> assertThat(response.getBody().data().product().brand().name()).isEqualTo("Nike")
            );
        }

        @DisplayName("존재하지 않는 브랜드로 등록하면, 404 Not Found 응답을 받는다.")
        @Test
        void returnsNotFound_whenBrandNotExists() {
            // Arrange
            var request = new ProductV1Dto.RegisterRequest(999L, "에어맥스 90", "설명", 139000L, 100, 5);

            // Act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                PRODUCT_ADMIN,
                HttpMethod.POST,
                adminEntity(request),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("이름이 빈 문자열이면, 400 Bad Request 응답을 받는다.")
        @Test
        void returnsBadRequest_whenNameIsBlank() {
            // Arrange
            Long brandId = registerBrand("Nike", "Just Do It");
            var request = new ProductV1Dto.RegisterRequest(brandId, "  ", "설명", 139000L, 100, 5);

            // Act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                PRODUCT_ADMIN,
                HttpMethod.POST,
                adminEntity(request),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("가격이 0이면, 400 Bad Request 응답을 받는다.")
        @Test
        void returnsBadRequest_whenPriceIsZero() {
            // Arrange
            Long brandId = registerBrand("Nike", "Just Do It");
            var request = new ProductV1Dto.RegisterRequest(brandId, "에어맥스 90", "설명", 0L, 100, 5);

            // Act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                PRODUCT_ADMIN,
                HttpMethod.POST,
                adminEntity(request),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("최대 주문 수량이 0이면, 400 Bad Request 응답을 받는다.")
        @Test
        void returnsBadRequest_whenMaxOrderQuantityIsZero() {
            // Arrange
            Long brandId = registerBrand("Nike", "Just Do It");
            var request = new ProductV1Dto.RegisterRequest(brandId, "에어맥스 90", "설명", 139000L, 100, 0);

            // Act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                PRODUCT_ADMIN,
                HttpMethod.POST,
                adminEntity(request),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/products (상품 목록 조회)")
    @Nested
    class GetProducts {

        @DisplayName("상품이 존재하면, 목록을 반환한다.")
        @Test
        void returnsProducts_whenProductsExist() {
            // Arrange
            Long brandId = registerBrand("Nike", "Just Do It");
            registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);
            registerProduct(brandId, "에어포스 1", 119000L, 50, 3);

            // Act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductListResponse>> response = testRestTemplate.exchange(
                PRODUCT_PUBLIC,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().products()).hasSize(2)
            );
        }

        @DisplayName("키워드로 상품명을 검색하면, 일치하는 상품만 반환한다.")
        @Test
        void returnsFilteredProducts_whenKeywordMatchesProductName() {
            // Arrange
            Long brandId = registerBrand("Nike", "Just Do It");
            registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);
            registerProduct(brandId, "에어포스 1", 119000L, 50, 3);

            // Act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductListResponse>> response = testRestTemplate.exchange(
                PRODUCT_PUBLIC + "?keyword=에어맥스",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().products()).hasSize(1),
                () -> assertThat(response.getBody().data().products().get(0).name()).isEqualTo("에어맥스 90")
            );
        }

        @DisplayName("키워드로 브랜드명을 검색하면, 해당 브랜드의 상품을 반환한다.")
        @Test
        void returnsFilteredProducts_whenKeywordMatchesBrandName() {
            // Arrange
            Long nikeId = registerBrand("Nike", "Just Do It");
            Long adidasId = registerBrand("Adidas", "Impossible Is Nothing");
            registerProduct(nikeId, "에어맥스 90", 139000L, 100, 5);
            registerProduct(adidasId, "울트라부스트", 189000L, 30, 2);

            // Act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductListResponse>> response = testRestTemplate.exchange(
                PRODUCT_PUBLIC + "?keyword=Nike",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().products()).hasSize(1),
                () -> assertThat(response.getBody().data().products().get(0).name()).isEqualTo("에어맥스 90")
            );
        }

        @DisplayName("브랜드 ID로 필터하면, 해당 브랜드의 상품만 반환한다.")
        @Test
        void returnsFilteredProducts_whenBrandIdProvided() {
            // Arrange
            Long nikeId = registerBrand("Nike", "Just Do It");
            Long adidasId = registerBrand("Adidas", "Impossible Is Nothing");
            registerProduct(nikeId, "에어맥스 90", 139000L, 100, 5);
            registerProduct(adidasId, "울트라부스트", 189000L, 30, 2);

            // Act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductListResponse>> response = testRestTemplate.exchange(
                PRODUCT_PUBLIC + "?brandId=" + nikeId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().products()).hasSize(1),
                () -> assertThat(response.getBody().data().products().get(0).name()).isEqualTo("에어맥스 90")
            );
        }

        @DisplayName("가격 오름차순으로 정렬하면, 가격 순서대로 반환한다.")
        @Test
        void returnsSortedProducts_whenSortByPriceAsc() {
            // Arrange
            Long brandId = registerBrand("Nike", "Just Do It");
            registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);
            registerProduct(brandId, "에어포스 1", 119000L, 50, 3);
            registerProduct(brandId, "덩크 로우", 159000L, 80, 4);

            // Act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductListResponse>> response = testRestTemplate.exchange(
                PRODUCT_PUBLIC + "?sort=PRICE_ASC",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().products()).hasSize(3),
                () -> assertThat(response.getBody().data().products().get(0).price()).isEqualTo(119000L),
                () -> assertThat(response.getBody().data().products().get(1).price()).isEqualTo(139000L),
                () -> assertThat(response.getBody().data().products().get(2).price()).isEqualTo(159000L)
            );
        }

        @DisplayName("존재하지 않는 정렬 타입이면, 400 Bad Request 응답을 받는다.")
        @Test
        void returnsBadRequest_whenInvalidSortType() {
            // Act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductListResponse>> response = testRestTemplate.exchange(
                PRODUCT_PUBLIC + "?sort=INVALID_SORT",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/products/{productId} (상품 상세 조회)")
    @Nested
    class GetProduct {

        @DisplayName("존재하는 상품을 조회하면, 브랜드 정보가 포함된 200 OK 응답을 받는다.")
        @Test
        void returnsOkWithBrandInfo_whenProductExists() {
            // Arrange
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);

            // Act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                PRODUCT_PUBLIC + "/" + productId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().product().name()).isEqualTo("에어맥스 90"),
                () -> assertThat(response.getBody().data().product().price()).isEqualTo(139000L),
                () -> assertThat(response.getBody().data().product().brand().name()).isEqualTo("Nike")
            );
        }

        @DisplayName("존재하지 않는 상품을 조회하면, 404 Not Found 응답을 받는다.")
        @Test
        void returnsNotFound_whenProductNotExists() {
            // Act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                PRODUCT_PUBLIC + "/999",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("PUT /api-admin/v1/products/{productId} (상품 수정)")
    @Nested
    class Update {

        @DisplayName("유효한 정보로 수정하면, 200 OK 응답을 받는다.")
        @Test
        void returnsOk_whenValidRequest() {
            // Arrange
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);
            var request = new ProductV1Dto.UpdateRequest("에어맥스 95", "업데이트된 설명", 149000L, 3);

            // Act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                PRODUCT_ADMIN + "/" + productId,
                HttpMethod.PUT,
                adminEntity(request),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 수정 확인
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> getResponse = testRestTemplate.exchange(
                PRODUCT_PUBLIC + "/" + productId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );
            assertAll(
                () -> assertThat(getResponse.getBody().data().product().name()).isEqualTo("에어맥스 95"),
                () -> assertThat(getResponse.getBody().data().product().price()).isEqualTo(149000L),
                () -> assertThat(getResponse.getBody().data().product().maxOrderQuantity()).isEqualTo(3)
            );
        }

        @DisplayName("존재하지 않는 상품을 수정하면, 404 Not Found 응답을 받는다.")
        @Test
        void returnsNotFound_whenProductNotExists() {
            // Act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                PRODUCT_ADMIN + "/999",
                HttpMethod.PUT,
                adminEntity(new ProductV1Dto.UpdateRequest("이름", "설명", 10000L, 5)),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("DELETE /api-admin/v1/products/{productId} (상품 삭제)")
    @Nested
    class Delete {

        @DisplayName("존재하는 상품을 삭제하면, 200 OK 응답을 받고 조회 시 404가 반환된다.")
        @Test
        void returnsOk_whenProductExists() {
            // Arrange
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);

            // Act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                PRODUCT_ADMIN + "/" + productId,
                HttpMethod.DELETE,
                adminEntity(null),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 삭제 후 조회 시 NOT_FOUND
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> getResponse = testRestTemplate.exchange(
                PRODUCT_PUBLIC + "/" + productId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 상품을 삭제하면, 404 Not Found 응답을 받는다.")
        @Test
        void returnsNotFound_whenProductNotExists() {
            // Act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                PRODUCT_ADMIN + "/999",
                HttpMethod.DELETE,
                adminEntity(null),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("PATCH /api-admin/v1/products/{productId}/stock (재고 수정)")
    @Nested
    class UpdateStock {

        @DisplayName("유효한 수량으로 수정하면, 200 OK 응답을 받는다.")
        @Test
        void returnsOk_whenValidRequest() {
            // Arrange
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);

            // Act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                PRODUCT_ADMIN + "/" + productId + "/stock",
                HttpMethod.PATCH,
                adminEntity(new ProductV1Dto.UpdateStockRequest(200)),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 재고 수정 확인 (별도 stock API로 조회)
            ResponseEntity<ApiResponse<ProductV1Dto.StockResponse>> stockResponse = testRestTemplate.exchange(
                PRODUCT_PUBLIC + "/" + productId + "/stock",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );
            assertThat(stockResponse.getBody().data().stockQuantity()).isEqualTo(200);
        }

        @DisplayName("음수 수량으로 수정하면, 400 Bad Request 응답을 받는다.")
        @Test
        void returnsBadRequest_whenQuantityIsNegative() {
            // Arrange
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);

            // Act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                PRODUCT_ADMIN + "/" + productId + "/stock",
                HttpMethod.PATCH,
                adminEntity(new ProductV1Dto.UpdateStockRequest(-1)),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 상품의 재고를 수정하면, 404 Not Found 응답을 받는다.")
        @Test
        void returnsNotFound_whenProductNotExists() {
            // Act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                PRODUCT_ADMIN + "/999/stock",
                HttpMethod.PATCH,
                adminEntity(new ProductV1Dto.UpdateStockRequest(200)),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("브랜드 삭제 시 상품 cascade 삭제")
    @Nested
    class BrandCascadeDelete {

        @DisplayName("브랜드를 삭제하면, 해당 브랜드의 모든 상품도 삭제된다.")
        @Test
        void deletesProducts_whenBrandDeleted() {
            // Arrange
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId1 = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);
            Long productId2 = registerProduct(brandId, "에어포스 1", 119000L, 50, 3);

            // Act
            testRestTemplate.exchange(
                "/api-admin/v1/brands/" + brandId,
                HttpMethod.DELETE,
                adminEntity(null),
                new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            // Assert - 상품 조회 시 NOT_FOUND
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response1 = testRestTemplate.exchange(
                PRODUCT_PUBLIC + "/" + productId1,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response2 = testRestTemplate.exchange(
                PRODUCT_PUBLIC + "/" + productId2,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );
            assertAll(
                () -> assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
        }
    }

    // Helper methods
    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        headers.set("Content-Type", "application/json");
        return headers;
    }

    private <T> HttpEntity<T> adminEntity(T body) {
        return new HttpEntity<>(body, adminHeaders());
    }

    private Long registerBrand(String name, String description) {
        var request = new BrandV1Dto.RegisterRequest(name, description);
        ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
            BRAND_ADMIN,
            HttpMethod.POST,
            adminEntity(request),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().brand().id();
    }

    private Long registerProduct(Long brandId, String name, Long price, int stock, int maxOrder) {
        var request = new ProductV1Dto.RegisterRequest(brandId, name, "설명", price, stock, maxOrder);
        ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
            PRODUCT_ADMIN,
            HttpMethod.POST,
            adminEntity(request),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().product().id();
    }
}

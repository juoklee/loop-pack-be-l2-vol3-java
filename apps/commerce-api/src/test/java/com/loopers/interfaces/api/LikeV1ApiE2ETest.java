package com.loopers.interfaces.api;

import com.loopers.application.like.LikeCountSyncScheduler;
import com.loopers.interfaces.api.brand.BrandV1Dto;
import com.loopers.interfaces.api.like.LikeV1Dto;
import com.loopers.interfaces.api.member.MemberV1Dto;
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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeV1ApiE2ETest {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final CacheManager cacheManager;
    private final LikeCountSyncScheduler likeCountSyncScheduler;

    @Autowired
    public LikeV1ApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp,
                            CacheManager cacheManager, LikeCountSyncScheduler likeCountSyncScheduler) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.cacheManager = cacheManager;
        this.likeCountSyncScheduler = likeCountSyncScheduler;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @DisplayName("POST /api/v1/products/{productId}/likes (상품 좋아요 토글)")
    @Nested
    class ToggleProductLike {

        @DisplayName("첫 좋아요 시, liked=true와 likeCount=1을 반환한다.")
        @Test
        void returnsLikedTrue_whenFirstLike() {
            // Arrange
            registerMember("user1", "Test1234!");
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);

            // Act
            ResponseEntity<ApiResponse<LikeV1Dto.ToggleResponse>> response = testRestTemplate.exchange(
                "/api/v1/products/" + productId + "/likes",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().liked()).isTrue(),
                () -> assertThat(response.getBody().data().likeCount()).isEqualTo(1)
            );
        }

        @DisplayName("다시 토글하면, liked=false와 likeCount=0을 반환한다.")
        @Test
        void returnsLikedFalse_whenToggleAgain() {
            // Arrange
            registerMember("user1", "Test1234!");
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);
            HttpHeaders headers = authHeaders("user1", "Test1234!");

            // 첫 좋아요
            testRestTemplate.exchange(
                "/api/v1/products/" + productId + "/likes",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<ApiResponse<LikeV1Dto.ToggleResponse>>() {}
            );

            // Act - 두 번째 토글
            ResponseEntity<ApiResponse<LikeV1Dto.ToggleResponse>> response = testRestTemplate.exchange(
                "/api/v1/products/" + productId + "/likes",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().liked()).isFalse(),
                () -> assertThat(response.getBody().data().likeCount()).isZero()
            );
        }

        @DisplayName("좋아요 후 스케줄러 동기화 전까지 상품 상세의 likeCount는 0이다.")
        @Test
        void likeCountNotReflectedBeforeSync() {
            // Arrange
            registerMember("user1", "Test1234!");
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);

            // Act - 좋아요 (MV 방식: product.likeCount는 즉시 갱신되지 않음)
            testRestTemplate.exchange(
                "/api/v1/products/" + productId + "/likes",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<ApiResponse<LikeV1Dto.ToggleResponse>>() {}
            );

            // Assert - 스케줄러 실행 전이므로 상품 상세의 likeCount는 아직 0
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> productResponse = testRestTemplate.exchange(
                "/api/v1/products/" + productId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );
            assertThat(productResponse.getBody().data().product().likeCount()).isZero();
        }

        @DisplayName("좋아요 후 스케줄러 동기화하면 상품 상세에 likeCount가 반영된다.")
        @Test
        void likeCountReflectedAfterSync() {
            // Arrange
            registerMember("user1", "Test1234!");
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);

            // Act - 좋아요
            testRestTemplate.exchange(
                "/api/v1/products/" + productId + "/likes",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<ApiResponse<LikeV1Dto.ToggleResponse>>() {}
            );

            // 스케줄러 수동 실행
            likeCountSyncScheduler.syncLikeCounts();

            // Assert - 동기화 후 likeCount 반영
            cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> productResponse = testRestTemplate.exchange(
                "/api/v1/products/" + productId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );
            assertThat(productResponse.getBody().data().product().likeCount()).isEqualTo(1);
        }

        @DisplayName("존재하지 않는 상품에 좋아요하면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenProductNotExists() {
            // Arrange
            registerMember("user1", "Test1234!");

            // Act
            ResponseEntity<ApiResponse<LikeV1Dto.ToggleResponse>> response = testRestTemplate.exchange(
                "/api/v1/products/9999/likes",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("인증 없이 접근하면, 401 Unauthorized를 반환한다.")
        @Test
        void returnsUnauthorized_whenNoAuth() {
            // Act
            ResponseEntity<ApiResponse<LikeV1Dto.ToggleResponse>> response = testRestTemplate.exchange(
                "/api/v1/products/1/likes",
                HttpMethod.POST,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("POST /api/v1/brands/{brandId}/likes (브랜드 좋아요 토글)")
    @Nested
    class ToggleBrandLike {

        @DisplayName("첫 좋아요 시, liked=true와 likeCount=1을 반환한다.")
        @Test
        void returnsLikedTrue_whenFirstLike() {
            // Arrange
            registerMember("user1", "Test1234!");
            Long brandId = registerBrand("Nike", "Just Do It");

            // Act
            ResponseEntity<ApiResponse<LikeV1Dto.ToggleResponse>> response = testRestTemplate.exchange(
                "/api/v1/brands/" + brandId + "/likes",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().liked()).isTrue(),
                () -> assertThat(response.getBody().data().likeCount()).isEqualTo(1)
            );
        }

        @DisplayName("다시 토글하면, liked=false와 likeCount=0을 반환한다.")
        @Test
        void returnsLikedFalse_whenToggleAgain() {
            // Arrange
            registerMember("user1", "Test1234!");
            Long brandId = registerBrand("Nike", "Just Do It");
            HttpHeaders headers = authHeaders("user1", "Test1234!");

            testRestTemplate.exchange(
                "/api/v1/brands/" + brandId + "/likes",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<ApiResponse<LikeV1Dto.ToggleResponse>>() {}
            );

            // Act
            ResponseEntity<ApiResponse<LikeV1Dto.ToggleResponse>> response = testRestTemplate.exchange(
                "/api/v1/brands/" + brandId + "/likes",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().liked()).isFalse(),
                () -> assertThat(response.getBody().data().likeCount()).isZero()
            );
        }

        @DisplayName("인증 없이 접근하면, 401 Unauthorized를 반환한다.")
        @Test
        void returnsUnauthorized_whenNoAuth() {
            // Act
            ResponseEntity<ApiResponse<LikeV1Dto.ToggleResponse>> response = testRestTemplate.exchange(
                "/api/v1/brands/1/likes",
                HttpMethod.POST,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api/v1/members/me/likes/products (내 상품 좋아요 목록)")
    @Nested
    class GetMyLikedProducts {

        @DisplayName("좋아요한 상품만 반환한다.")
        @Test
        void returnsOnlyLikedProducts() {
            // Arrange
            registerMember("user1", "Test1234!");
            Long brandId = registerBrand("Nike", "Just Do It");
            Long product1 = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);
            Long product2 = registerProduct(brandId, "에어포스 1", 119000L, 50, 3);
            registerProduct(brandId, "덩크 로우", 159000L, 80, 4); // 좋아요 안 함

            HttpHeaders headers = authHeaders("user1", "Test1234!");
            toggleProductLike(headers, product1);
            toggleProductLike(headers, product2);

            // Act
            ResponseEntity<ApiResponse<LikeV1Dto.ProductLikeListResponse>> response = testRestTemplate.exchange(
                "/api/v1/members/me/likes/products",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().products()).hasSize(2)
            );
        }

        @DisplayName("좋아요한 상품이 없으면, 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoLikes() {
            // Arrange
            registerMember("user1", "Test1234!");

            // Act
            ResponseEntity<ApiResponse<LikeV1Dto.ProductLikeListResponse>> response = testRestTemplate.exchange(
                "/api/v1/members/me/likes/products",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().products()).isEmpty()
            );
        }

        @DisplayName("인증 없이 접근하면, 401 Unauthorized를 반환한다.")
        @Test
        void returnsUnauthorized_whenNoAuth() {
            // Act
            ResponseEntity<ApiResponse<LikeV1Dto.ProductLikeListResponse>> response = testRestTemplate.exchange(
                "/api/v1/members/me/likes/products",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api/v1/members/me/likes/brands (내 브랜드 좋아요 목록)")
    @Nested
    class GetMyLikedBrands {

        @DisplayName("좋아요한 브랜드만 반환한다.")
        @Test
        void returnsOnlyLikedBrands() {
            // Arrange
            registerMember("user1", "Test1234!");
            Long brand1 = registerBrand("Nike", "Just Do It");
            Long brand2 = registerBrand("Adidas", "Impossible Is Nothing");
            registerBrand("Puma", "Forever Faster"); // 좋아요 안 함

            HttpHeaders headers = authHeaders("user1", "Test1234!");
            toggleBrandLike(headers, brand1);
            toggleBrandLike(headers, brand2);

            // Act
            ResponseEntity<ApiResponse<LikeV1Dto.BrandLikeListResponse>> response = testRestTemplate.exchange(
                "/api/v1/members/me/likes/brands",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().brands()).hasSize(2)
            );
        }

        @DisplayName("인증 없이 접근하면, 401 Unauthorized를 반환한다.")
        @Test
        void returnsUnauthorized_whenNoAuth() {
            // Act
            ResponseEntity<ApiResponse<LikeV1Dto.BrandLikeListResponse>> response = testRestTemplate.exchange(
                "/api/v1/members/me/likes/brands",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // --- Helper Methods ---

    private void registerMember(String loginId, String password) {
        MemberV1Dto.RegisterRequest request = new MemberV1Dto.RegisterRequest(
            loginId, password, "홍길동", LocalDate.of(1990, 1, 15),
            "MALE", loginId + "@example.com", null
        );
        testRestTemplate.exchange(
            "/api/v1/members",
            HttpMethod.POST,
            new HttpEntity<>(request),
            new ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {}
        );
    }

    private Long registerBrand(String name, String description) {
        var request = new BrandV1Dto.RegisterRequest(name, description);
        ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
            "/api-admin/v1/brands",
            HttpMethod.POST,
            adminEntity(request),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().brand().id();
    }

    private Long registerProduct(Long brandId, String name, Long price, int stock, int maxOrder) {
        var request = new ProductV1Dto.RegisterRequest(brandId, name, "설명", price, stock, maxOrder);
        ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
            "/api-admin/v1/products",
            HttpMethod.POST,
            adminEntity(request),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().product().id();
    }

    private void toggleProductLike(HttpHeaders headers, Long productId) {
        testRestTemplate.exchange(
            "/api/v1/products/" + productId + "/likes",
            HttpMethod.POST,
            new HttpEntity<>(headers),
            new ParameterizedTypeReference<ApiResponse<LikeV1Dto.ToggleResponse>>() {}
        );
    }

    private void toggleBrandLike(HttpHeaders headers, Long brandId) {
        testRestTemplate.exchange(
            "/api/v1/brands/" + brandId + "/likes",
            HttpMethod.POST,
            new HttpEntity<>(headers),
            new ParameterizedTypeReference<ApiResponse<LikeV1Dto.ToggleResponse>>() {}
        );
    }

    private HttpHeaders authHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, loginId);
        headers.set(HEADER_LOGIN_PW, password);
        return headers;
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        headers.set("Content-Type", "application/json");
        return headers;
    }

    private <T> HttpEntity<T> adminEntity(T body) {
        return new HttpEntity<>(body, adminHeaders());
    }
}

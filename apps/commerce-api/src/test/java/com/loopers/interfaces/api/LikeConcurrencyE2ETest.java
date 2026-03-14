package com.loopers.interfaces.api;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeConcurrencyE2ETest {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final CacheManager cacheManager;

    @Autowired
    public LikeConcurrencyE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp, CacheManager cacheManager) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.cacheManager = cacheManager;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @DisplayName("상품 좋아요 동시성 테스트")
    @Nested
    class ProductLikeConcurrency {

        @DisplayName("동시에 10명이 같은 상품에 좋아요를 누르면, likeCount가 정확히 10이 된다.")
        @Test
        void concurrentLikes_incrementCorrectly() throws InterruptedException {
            // Arrange
            int threadCount = 10;
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 10000L, 100, 5);

            String[] loginIds = new String[threadCount];
            for (int i = 0; i < threadCount; i++) {
                String loginId = "user" + i;
                registerMember(loginId, "Test1234!");
                loginIds[i] = loginId;
            }

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            // Act — 10명이 동시에 좋아요
            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                            "/api/v1/products/" + productId + "/likes", HttpMethod.POST,
                            new HttpEntity<>(authHeaders(loginIds[idx], "Test1234!")),
                            new ParameterizedTypeReference<>() {}
                        );

                        if (response.getStatusCode() == HttpStatus.OK) {
                            successCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executor.shutdown();

            // Assert — likeCount 확인
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> productResponse = testRestTemplate.exchange(
                "/api/v1/products/" + productId, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            assertThat(successCount.get()).isEqualTo(threadCount);
            assertThat(productResponse.getBody().data().product().likeCount()).isEqualTo(10);
        }
    }

    @DisplayName("좋아요 토글 동시성 테스트")
    @Nested
    class LikeToggleConcurrency {

        @DisplayName("같은 사용자가 동시에 좋아요를 2번 토글하면, 최종 likeCount가 0 또는 1이다.")
        @Test
        void concurrentLikeToggle_finalStateConsistent() throws InterruptedException {
            // Arrange
            int threadCount = 2;
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 10000L, 100, 5);

            registerMember("toggleUser", "Test1234!");

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // Act — 같은 사용자가 동시에 2번 좋아요 토글
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        ResponseEntity<ApiResponse<LikeV1Dto.ToggleResponse>> response = testRestTemplate.exchange(
                            "/api/v1/products/" + productId + "/likes", HttpMethod.POST,
                            new HttpEntity<>(authHeaders("toggleUser", "Test1234!")),
                            new ParameterizedTypeReference<>() {}
                        );

                        if (response.getStatusCode() == HttpStatus.OK) {
                            successCount.incrementAndGet();
                        } else {
                            failCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executor.shutdown();

            // Assert — likeCount가 0 또는 1 (동시 토글이므로 둘 다 가능)
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> productResponse = testRestTemplate.exchange(
                "/api/v1/products/" + productId, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            int likeCount = productResponse.getBody().data().product().likeCount();
            assertThat(likeCount).isIn(0, 1);
            // 성공 + 실패 = threadCount (Unique 제약조건으로 하나가 실패할 수도 있음)
            assertThat(successCount.get() + failCount.get()).isEqualTo(threadCount);
        }
    }

    @DisplayName("브랜드 좋아요 동시성 테스트")
    @Nested
    class BrandLikeConcurrency {

        @DisplayName("동시에 10명이 같은 브랜드에 좋아요를 누르면, likeCount가 정확히 10이 된다.")
        @Test
        void concurrentBrandLikes_incrementCorrectly() throws InterruptedException {
            // Arrange
            int threadCount = 10;
            Long brandId = registerBrand("Nike", "Just Do It");

            String[] loginIds = new String[threadCount];
            for (int i = 0; i < threadCount; i++) {
                String loginId = "brandLikeUser" + i;
                registerMember(loginId, "Test1234!");
                loginIds[i] = loginId;
            }

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            // Act — 10명이 동시에 브랜드 좋아요
            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        ResponseEntity<ApiResponse<LikeV1Dto.ToggleResponse>> response = testRestTemplate.exchange(
                            "/api/v1/brands/" + brandId + "/likes", HttpMethod.POST,
                            new HttpEntity<>(authHeaders(loginIds[idx], "Test1234!")),
                            new ParameterizedTypeReference<>() {}
                        );

                        if (response.getStatusCode() == HttpStatus.OK) {
                            successCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executor.shutdown();

            // Assert — likeCount 확인
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> brandResponse = testRestTemplate.exchange(
                "/api/v1/brands/" + brandId, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            assertThat(successCount.get()).isEqualTo(threadCount);
            assertThat(brandResponse.getBody().data().brand().likeCount()).isEqualTo(10);
        }
    }

    // --- Helper Methods ---

    private void registerMember(String loginId, String password) {
        var request = new MemberV1Dto.RegisterRequest(
            loginId, password, "홍길동", LocalDate.of(1990, 1, 15),
            "MALE", loginId + "@example.com", null
        );
        testRestTemplate.exchange(
            "/api/v1/members", HttpMethod.POST,
            new HttpEntity<>(request),
            new ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {}
        );
    }

    private Long registerBrand(String name, String description) {
        var request = new BrandV1Dto.RegisterRequest(name, description);
        ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
            "/api-admin/v1/brands", HttpMethod.POST, adminEntity(request),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().brand().id();
    }

    private Long registerProduct(Long brandId, String name, Long price, int stock, int maxOrder) {
        var request = new ProductV1Dto.RegisterRequest(brandId, name, "설명", price, stock, maxOrder);
        ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
            "/api-admin/v1/products", HttpMethod.POST, adminEntity(request),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().product().id();
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

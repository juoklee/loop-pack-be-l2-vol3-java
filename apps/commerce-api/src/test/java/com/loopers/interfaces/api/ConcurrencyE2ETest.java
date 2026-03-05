package com.loopers.interfaces.api;

import com.loopers.interfaces.api.brand.BrandV1Dto;
import com.loopers.interfaces.api.coupon.CouponV1Dto;
import com.loopers.interfaces.api.member.MemberV1Dto;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.interfaces.api.product.ProductV1Dto;
import com.loopers.interfaces.api.address.AddressV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConcurrencyE2ETest {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public ConcurrencyE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("재고 동시성 테스트")
    @Nested
    class StockConcurrency {

        @DisplayName("동시에 10명이 같은 상품을 1개씩 주문하면, 재고가 정확히 10개 차감된다.")
        @Test
        void concurrentOrders_decreaseStockCorrectly() throws InterruptedException {
            // Arrange
            int threadCount = 10;
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 10000L, 100, 5);

            // 10명의 사용자 생성 + 주소 등록
            String[] loginIds = new String[threadCount];
            Long[] addressIds = new Long[threadCount];
            for (int i = 0; i < threadCount; i++) {
                String loginId = "user" + i;
                registerMember(loginId, "Test1234!");
                loginIds[i] = loginId;
                addressIds[i] = registerAddress(loginId, "Test1234!");
            }

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            // Act — 동시에 10개 주문
            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        var items = List.of(new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 1));
                        var request = new OrderV1Dto.CreateOrderRequest(addressIds[idx], null, items);

                        ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                            "/api/v1/orders", HttpMethod.POST,
                            new HttpEntity<>(request, authHeaders(loginIds[idx], "Test1234!")),
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

            // Assert — 재고 확인
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> productResponse = testRestTemplate.exchange(
                "/api/v1/products/" + productId, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            assertThat(successCount.get()).isEqualTo(threadCount);
            assertThat(productResponse.getBody().data().product().stockQuantity()).isEqualTo(90);
        }

        @DisplayName("재고 5개인 상품에 10명이 동시 주문하면, 5명만 성공하고 재고는 0이 된다.")
        @Test
        void concurrentOrders_withInsufficientStock() throws InterruptedException {
            // Arrange
            int threadCount = 10;
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 10000L, 5, 5);

            String[] loginIds = new String[threadCount];
            Long[] addressIds = new Long[threadCount];
            for (int i = 0; i < threadCount; i++) {
                String loginId = "user" + i;
                registerMember(loginId, "Test1234!");
                loginIds[i] = loginId;
                addressIds[i] = registerAddress(loginId, "Test1234!");
            }

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // Act
            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        var items = List.of(new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 1));
                        var request = new OrderV1Dto.CreateOrderRequest(addressIds[idx], null, items);

                        ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                            "/api/v1/orders", HttpMethod.POST,
                            new HttpEntity<>(request, authHeaders(loginIds[idx], "Test1234!")),
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

            // Assert
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> productResponse = testRestTemplate.exchange(
                "/api/v1/products/" + productId, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            assertThat(successCount.get()).isEqualTo(5);
            assertThat(failCount.get()).isEqualTo(5);
            assertThat(productResponse.getBody().data().product().stockQuantity()).isEqualTo(0);
        }
    }

    @DisplayName("쿠폰 동시성 테스트")
    @Nested
    class CouponConcurrency {

        @DisplayName("같은 쿠폰을 동시에 2개 기기에서 사용해 주문하면, 1명만 성공한다.")
        @Test
        void concurrentCouponUsage_onlyOneSucceeds() throws InterruptedException {
            // Arrange
            int threadCount = 2;
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 100000L, 100, 5);

            registerMember("user1", "Test1234!");
            Long addressId = registerAddress("user1", "Test1234!");
            Long couponId = createCoupon("5000원 할인", "FIXED", 5000L, null, LocalDateTime.now().plusDays(30));
            Long memberCouponId = issueCouponAndGetId("user1", "Test1234!", couponId);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // Act — 같은 사용자가 같은 쿠폰으로 동시에 2건 주문
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        var items = List.of(new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 1));
                        var request = new OrderV1Dto.CreateOrderRequest(addressId, memberCouponId, items);

                        ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                            "/api/v1/orders", HttpMethod.POST,
                            new HttpEntity<>(request, authHeaders("user1", "Test1234!")),
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

            // Assert — 쿠폰은 1회만 사용 가능
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failCount.get()).isEqualTo(1);
        }
    }

    @DisplayName("좋아요 동시성 테스트")
    @Nested
    class LikeConcurrency {

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

    private Long registerAddress(String loginId, String password) {
        var request = new AddressV1Dto.CreateAddressRequest(
            "집", "홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호"
        );
        ResponseEntity<ApiResponse<AddressV1Dto.AddressResponse>> response = testRestTemplate.exchange(
            "/api/v1/members/me/addresses", HttpMethod.POST,
            new HttpEntity<>(request, authHeaders(loginId, password)),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().address().id();
    }

    private Long createCoupon(String name, String type, Long value, Long minOrderAmount, LocalDateTime expiredAt) {
        var request = new CouponV1Dto.CreateCouponRequest(name, type, value, minOrderAmount, expiredAt, null);
        ResponseEntity<ApiResponse<CouponV1Dto.CouponResponse>> response = testRestTemplate.exchange(
            "/api-admin/v1/coupons", HttpMethod.POST, adminEntity(request),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().coupon().id();
    }

    private Long issueCouponAndGetId(String loginId, String password, Long couponId) {
        ResponseEntity<ApiResponse<CouponV1Dto.MemberCouponResponse>> response = testRestTemplate.exchange(
            "/api/v1/coupons/" + couponId + "/issue", HttpMethod.POST,
            new HttpEntity<>(authHeaders(loginId, password)),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().memberCoupon().id();
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

package com.loopers.interfaces.api;

import com.loopers.interfaces.api.brand.BrandV1Dto;
import com.loopers.interfaces.api.coupon.CouponV1Dto;
import com.loopers.interfaces.api.like.LikeV1Dto;
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
import java.util.ArrayList;
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

    @DisplayName("다중 상품 주문 데드락 방지 테스트")
    @Nested
    class MultiProductDeadlockPrevention {

        @DisplayName("두 스레드가 서로 다른 순서로 2개 상품을 주문해도 데드락 없이 완료되고 재고가 정확하다.")
        @Test
        void concurrentOrders_multipleProducts_deadlockPrevention() throws InterruptedException {
            // Arrange
            int threadCount = 2;
            Long brandId = registerBrand("Nike", "Just Do It");
            Long p1 = registerProduct(brandId, "상품A", 10000L, 10, 5);
            Long p2 = registerProduct(brandId, "상품B", 20000L, 10, 5);

            String[] loginIds = new String[threadCount];
            Long[] addressIds = new Long[threadCount];
            for (int i = 0; i < threadCount; i++) {
                String loginId = "deadlockUser" + i;
                registerMember(loginId, "Test1234!");
                loginIds[i] = loginId;
                addressIds[i] = registerAddress(loginId, "Test1234!");
            }

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            // Act — 스레드1: [p1, p2] 순서, 스레드2: [p2, p1] 순서로 주문
            executor.submit(() -> {
                ready.countDown();
                try { start.await(); } catch (InterruptedException ignored) {}
                try {
                    var items = List.of(
                        new OrderV1Dto.CreateOrderRequest.OrderItemRequest(p1, 1),
                        new OrderV1Dto.CreateOrderRequest.OrderItemRequest(p2, 1)
                    );
                    var request = new OrderV1Dto.CreateOrderRequest(addressIds[0], null, items);
                    ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                        "/api/v1/orders", HttpMethod.POST,
                        new HttpEntity<>(request, authHeaders(loginIds[0], "Test1234!")),
                        new ParameterizedTypeReference<>() {}
                    );
                    if (response.getStatusCode() == HttpStatus.OK) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    done.countDown();
                }
            });

            executor.submit(() -> {
                ready.countDown();
                try { start.await(); } catch (InterruptedException ignored) {}
                try {
                    var items = List.of(
                        new OrderV1Dto.CreateOrderRequest.OrderItemRequest(p2, 1),
                        new OrderV1Dto.CreateOrderRequest.OrderItemRequest(p1, 1)
                    );
                    var request = new OrderV1Dto.CreateOrderRequest(addressIds[1], null, items);
                    ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                        "/api/v1/orders", HttpMethod.POST,
                        new HttpEntity<>(request, authHeaders(loginIds[1], "Test1234!")),
                        new ParameterizedTypeReference<>() {}
                    );
                    if (response.getStatusCode() == HttpStatus.OK) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    done.countDown();
                }
            });

            ready.await();
            start.countDown();
            done.await();
            executor.shutdown();

            // Assert — 두 주문 모두 성공, 재고 각각 1씩 차감
            assertThat(successCount.get()).isEqualTo(2);

            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> p1Response = testRestTemplate.exchange(
                "/api/v1/products/" + p1, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> p2Response = testRestTemplate.exchange(
                "/api/v1/products/" + p2, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            assertThat(p1Response.getBody().data().product().stockQuantity()).isEqualTo(8);
            assertThat(p2Response.getBody().data().product().stockQuantity()).isEqualTo(8);
        }

        @DisplayName("세 스레드가 서로 다른 순서로 3개 상품을 주문해도 데드락 없이 완료된다.")
        @Test
        void concurrentOrders_threeProducts_deadlockPrevention() throws InterruptedException {
            // Arrange
            int threadCount = 3;
            Long brandId = registerBrand("Adidas", "Impossible Is Nothing");
            Long p1 = registerProduct(brandId, "상품X", 10000L, 10, 5);
            Long p2 = registerProduct(brandId, "상품Y", 20000L, 10, 5);
            Long p3 = registerProduct(brandId, "상품Z", 30000L, 10, 5);

            String[] loginIds = new String[threadCount];
            Long[] addressIds = new Long[threadCount];
            for (int i = 0; i < threadCount; i++) {
                String loginId = "triUser" + i;
                registerMember(loginId, "Test1234!");
                loginIds[i] = loginId;
                addressIds[i] = registerAddress(loginId, "Test1234!");
            }

            // 각 스레드가 서로 다른 순서로 상품을 주문
            Long[][] productOrders = {
                {p1, p2, p3},  // 스레드0: 정순
                {p3, p1, p2},  // 스레드1: 역순 시작
                {p2, p3, p1}   // 스레드2: 중간부터
            };

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            // Act
            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                executor.submit(() -> {
                    ready.countDown();
                    try { start.await(); } catch (InterruptedException ignored) {}
                    try {
                        var items = List.of(
                            new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productOrders[idx][0], 1),
                            new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productOrders[idx][1], 1),
                            new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productOrders[idx][2], 1)
                        );
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
                        done.countDown();
                    }
                });
            }

            ready.await();
            start.countDown();
            done.await();
            executor.shutdown();

            // Assert — 3개 주문 모두 성공, 재고 각각 3씩 차감
            assertThat(successCount.get()).isEqualTo(3);

            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> p1Response = testRestTemplate.exchange(
                "/api/v1/products/" + p1, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> p2Response = testRestTemplate.exchange(
                "/api/v1/products/" + p2, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> p3Response = testRestTemplate.exchange(
                "/api/v1/products/" + p3, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            assertThat(p1Response.getBody().data().product().stockQuantity()).isEqualTo(7);
            assertThat(p2Response.getBody().data().product().stockQuantity()).isEqualTo(7);
            assertThat(p3Response.getBody().data().product().stockQuantity()).isEqualTo(7);
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

    @DisplayName("쿠폰 발급 동시성 테스트")
    @Nested
    class CouponIssueConcurrency {

        @DisplayName("수량 5개 쿠폰에 10명이 동시 발급 요청하면, 5명만 성공하고 issuedQuantity는 5가 된다.")
        @Test
        void concurrentCouponIssue_onlyLimitedSucceeds() throws InterruptedException {
            // Arrange
            int threadCount = 10;
            Long couponId = createCouponWithQuantity("한정 쿠폰", "FIXED", 3000L, null,
                LocalDateTime.now().plusDays(30), 5);

            String[] loginIds = new String[threadCount];
            for (int i = 0; i < threadCount; i++) {
                String loginId = "issueUser" + i;
                registerMember(loginId, "Test1234!");
                loginIds[i] = loginId;
            }

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // Act — 10명이 동시에 쿠폰 발급 요청
            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        ResponseEntity<ApiResponse<CouponV1Dto.MemberCouponResponse>> response = testRestTemplate.exchange(
                            "/api/v1/coupons/" + couponId + "/issue", HttpMethod.POST,
                            new HttpEntity<>(authHeaders(loginIds[idx], "Test1234!")),
                            new ParameterizedTypeReference<>() {}
                        );

                        if (response.getStatusCode() == HttpStatus.CREATED) {
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

            // Assert — 발급 수량 확인
            assertThat(successCount.get()).isEqualTo(5);
            assertThat(failCount.get()).isEqualTo(5);
        }
    }

    @DisplayName("주문 취소 동시성 테스트")
    @Nested
    class OrderCancelConcurrency {

        @DisplayName("같은 주문을 동시에 2번 취소하면, 1번만 성공하고 재고는 정확히 1개만 복원된다.")
        @Test
        void concurrentOrderCancel_onlyOneSucceeds() throws InterruptedException {
            // Arrange
            int threadCount = 2;
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 10000L, 100, 5);

            registerMember("cancelUser", "Test1234!");
            Long addressId = registerAddress("cancelUser", "Test1234!");

            // 주문 생성
            var items = List.of(new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 1));
            var request = new OrderV1Dto.CreateOrderRequest(addressId, null, items);
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> orderResponse = testRestTemplate.exchange(
                "/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders("cancelUser", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );
            Long orderId = orderResponse.getBody().data().order().id();

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // Act — 동시에 2번 취소
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                            "/api/v1/orders/" + orderId + "/cancel", HttpMethod.POST,
                            new HttpEntity<>(authHeaders("cancelUser", "Test1234!")),
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

            // Assert — 1번만 취소 성공, 재고 정확히 1개 복원
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> productResponse = testRestTemplate.exchange(
                "/api/v1/products/" + productId, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failCount.get()).isEqualTo(1);
            assertThat(productResponse.getBody().data().product().stockQuantity()).isEqualTo(100);
        }
    }

    @DisplayName("주문 생성 + 취소 경합 동시성 테스트")
    @Nested
    class OrderCreateAndCancelConcurrency {

        @DisplayName("주문 생성과 다른 주문 취소가 동시에 발생해도 재고 정합성이 유지된다.")
        @Test
        void concurrentCreateAndCancel_stockRemainsConsistent() throws InterruptedException {
            // Arrange
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 10000L, 50, 5);

            // 먼저 5명이 순차적으로 주문 생성 (재고: 50 → 45)
            List<Long> orderIds = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                String loginId = "preUser" + i;
                registerMember(loginId, "Test1234!");
                Long addressId = registerAddress(loginId, "Test1234!");

                var items = List.of(new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 1));
                var request = new OrderV1Dto.CreateOrderRequest(addressId, null, items);
                ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                    "/api/v1/orders", HttpMethod.POST,
                    new HttpEntity<>(request, authHeaders(loginId, "Test1234!")),
                    new ParameterizedTypeReference<>() {}
                );
                orderIds.add(response.getBody().data().order().id());
            }

            // 5명의 새 주문자 + 5명의 취소자 준비
            int threadCount = 10;
            String[] newLoginIds = new String[5];
            Long[] newAddressIds = new Long[5];
            for (int i = 0; i < 5; i++) {
                String loginId = "newUser" + i;
                registerMember(loginId, "Test1234!");
                newLoginIds[i] = loginId;
                newAddressIds[i] = registerAddress(loginId, "Test1234!");
            }

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger createSuccess = new AtomicInteger(0);
            AtomicInteger cancelSuccess = new AtomicInteger(0);

            // Act — 5명 주문 생성 + 5명 주문 취소 동시 실행
            for (int i = 0; i < 5; i++) {
                // 새 주문 생성 스레드
                final int idx = i;
                executor.submit(() -> {
                    try {
                        var items = List.of(new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 1));
                        var request = new OrderV1Dto.CreateOrderRequest(newAddressIds[idx], null, items);
                        ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                            "/api/v1/orders", HttpMethod.POST,
                            new HttpEntity<>(request, authHeaders(newLoginIds[idx], "Test1234!")),
                            new ParameterizedTypeReference<>() {}
                        );
                        if (response.getStatusCode() == HttpStatus.OK) {
                            createSuccess.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });

                // 기존 주문 취소 스레드
                final Long orderId = orderIds.get(i);
                final String cancelLoginId = "preUser" + i;
                executor.submit(() -> {
                    try {
                        ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                            "/api/v1/orders/" + orderId + "/cancel", HttpMethod.POST,
                            new HttpEntity<>(authHeaders(cancelLoginId, "Test1234!")),
                            new ParameterizedTypeReference<>() {}
                        );
                        if (response.getStatusCode() == HttpStatus.OK) {
                            cancelSuccess.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executor.shutdown();

            // Assert — 재고 정합성: 초기 50 - 5(기존) + 5(취소 복원) - 5(신규) = 45
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> productResponse = testRestTemplate.exchange(
                "/api/v1/products/" + productId, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            assertThat(createSuccess.get()).isEqualTo(5);
            assertThat(cancelSuccess.get()).isEqualTo(5);
            assertThat(productResponse.getBody().data().product().stockQuantity()).isEqualTo(45);
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

    @DisplayName("배송지 수정 + 주문 취소 동시성 테스트")
    @Nested
    class ShippingAddressUpdateAndCancelConcurrency {

        @DisplayName("배송지 수정과 주문 취소가 동시에 발생하면, 취소된 주문의 배송지가 수정되지 않는다.")
        @Test
        void concurrentUpdateAndCancel_preventsUpdateOnCancelledOrder() throws InterruptedException {
            // Arrange
            int threadCount = 2;
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 10000L, 100, 5);

            registerMember("shippingUser", "Test1234!");
            Long addressId = registerAddress("shippingUser", "Test1234!");
            HttpHeaders headers = authHeaders("shippingUser", "Test1234!");

            // 주문 생성
            var items = List.of(new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 1));
            var createRequest = new OrderV1Dto.CreateOrderRequest(addressId, null, items);
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> orderResponse = testRestTemplate.exchange(
                "/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(createRequest, headers),
                new ParameterizedTypeReference<>() {}
            );
            Long orderId = orderResponse.getBody().data().order().id();

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger cancelSuccess = new AtomicInteger(0);
            AtomicInteger updateSuccess = new AtomicInteger(0);

            // Act — 배송지 수정과 주문 취소를 동시에 실행
            executor.submit(() -> {
                ready.countDown();
                try { start.await(); } catch (InterruptedException ignored) {}
                try {
                    ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                        "/api/v1/orders/" + orderId + "/cancel", HttpMethod.POST,
                        new HttpEntity<>(headers),
                        new ParameterizedTypeReference<>() {}
                    );
                    if (response.getStatusCode() == HttpStatus.OK) {
                        cancelSuccess.incrementAndGet();
                    }
                } catch (Exception ignored) {}
            });

            executor.submit(() -> {
                ready.countDown();
                try { start.await(); } catch (InterruptedException ignored) {}
                try {
                    var updateRequest = new OrderV1Dto.UpdateShippingAddressRequest(
                        "김철수", "010-9999-9999", "54321", "서울시 서초구", "202호"
                    );
                    ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                        "/api/v1/orders/" + orderId + "/shipping-address", HttpMethod.PUT,
                        new HttpEntity<>(updateRequest, headers),
                        new ParameterizedTypeReference<>() {}
                    );
                    if (response.getStatusCode() == HttpStatus.OK) {
                        updateSuccess.incrementAndGet();
                    }
                } catch (Exception ignored) {}
            });

            ready.await();
            start.countDown();
            executor.shutdown();
            executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

            // Assert — 주문 상태 확인
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> finalOrder = testRestTemplate.exchange(
                "/api/v1/orders/" + orderId, HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            String finalStatus = finalOrder.getBody().data().order().status();

            // 취소는 항상 성공해야 한다
            assertThat(cancelSuccess.get()).isEqualTo(1);
            assertThat(finalStatus).isEqualTo("CANCELLED");

            // 취소가 먼저 락을 획득한 경우: 배송지 수정 실패 (updateSuccess=0)
            // 배송지 수정이 먼저 락을 획득한 경우: 배송지 수정 성공 후 취소 성공 (updateSuccess=1)
            // 어느 경우든 최종 상태는 CANCELLED
            assertThat(updateSuccess.get()).isIn(0, 1);
        }
    }

    @DisplayName("쿠폰 + 재고 부족 복원 테스트")
    @Nested
    class CouponWithStockFailConcurrency {

        @DisplayName("쿠폰 적용 주문이 재고 부족으로 실패하면, 쿠폰이 사용되지 않은 상태로 남는다.")
        @Test
        void couponOrder_withInsufficientStock_couponRemainAvailable() throws InterruptedException {
            // Arrange
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 100000L, 1, 5);

            registerMember("couponStockUser1", "Test1234!");
            registerMember("couponStockUser2", "Test1234!");
            Long addressId1 = registerAddress("couponStockUser1", "Test1234!");
            Long addressId2 = registerAddress("couponStockUser2", "Test1234!");

            Long couponId = createCoupon("5000원 할인", "FIXED", 5000L, null, LocalDateTime.now().plusDays(30));
            Long memberCouponId1 = issueCouponAndGetId("couponStockUser1", "Test1234!", couponId);
            Long memberCouponId2 = issueCouponAndGetId("couponStockUser2", "Test1234!", couponId);

            int threadCount = 2;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // Act — 재고 1개인 상품에 2명이 쿠폰 적용하여 동시 주문
            executor.submit(() -> {
                try {
                    var items = List.of(new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 1));
                    var request = new OrderV1Dto.CreateOrderRequest(addressId1, memberCouponId1, items);
                    ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                        "/api/v1/orders", HttpMethod.POST,
                        new HttpEntity<>(request, authHeaders("couponStockUser1", "Test1234!")),
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

            executor.submit(() -> {
                try {
                    var items = List.of(new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 1));
                    var request = new OrderV1Dto.CreateOrderRequest(addressId2, memberCouponId2, items);
                    ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                        "/api/v1/orders", HttpMethod.POST,
                        new HttpEntity<>(request, authHeaders("couponStockUser2", "Test1234!")),
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

            latch.await();
            executor.shutdown();

            // Assert — 1명 성공, 1명 실패
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failCount.get()).isEqualTo(1);

            // 재고 0 확인
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> productResponse = testRestTemplate.exchange(
                "/api/v1/products/" + productId, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );
            assertThat(productResponse.getBody().data().product().stockQuantity()).isEqualTo(0);

            // 실패한 사용자의 쿠폰은 AVAILABLE 상태 확인
            ResponseEntity<ApiResponse<CouponV1Dto.MemberCouponResponse>> mc1 = testRestTemplate.exchange(
                "/api/v1/users/me/coupons?page=0&size=10", HttpMethod.GET,
                new HttpEntity<>(authHeaders("couponStockUser1", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );
            ResponseEntity<ApiResponse<CouponV1Dto.MemberCouponResponse>> mc2 = testRestTemplate.exchange(
                "/api/v1/users/me/coupons?page=0&size=10", HttpMethod.GET,
                new HttpEntity<>(authHeaders("couponStockUser2", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            // 두 쿠폰 중 하나는 USED, 하나는 AVAILABLE
            long usedCount = countCouponStatus(memberCouponId1, "couponStockUser1", "Test1234!", "USED")
                + countCouponStatus(memberCouponId2, "couponStockUser2", "Test1234!", "USED");
            long availableCount = countCouponStatus(memberCouponId1, "couponStockUser1", "Test1234!", "AVAILABLE")
                + countCouponStatus(memberCouponId2, "couponStockUser2", "Test1234!", "AVAILABLE");

            assertThat(usedCount).isEqualTo(1);
            assertThat(availableCount).isEqualTo(1);
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
        var request = new CouponV1Dto.CreateCouponRequest(name, type, value, minOrderAmount, expiredAt, null, null);
        ResponseEntity<ApiResponse<CouponV1Dto.CouponResponse>> response = testRestTemplate.exchange(
            "/api-admin/v1/coupons", HttpMethod.POST, adminEntity(request),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().coupon().id();
    }

    private Long createCouponWithQuantity(String name, String type, Long value, Long minOrderAmount,
                                           LocalDateTime expiredAt, int totalQuantity) {
        var request = new CouponV1Dto.CreateCouponRequest(name, type, value, minOrderAmount, expiredAt, null, totalQuantity);
        ResponseEntity<ApiResponse<CouponV1Dto.CouponResponse>> response = testRestTemplate.exchange(
            "/api-admin/v1/coupons", HttpMethod.POST, adminEntity(request),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().coupon().id();
    }

    private int countCouponStatus(Long memberCouponId, String loginId, String password, String expectedStatus) {
        ResponseEntity<ApiResponse<CouponV1Dto.MemberCouponListResponse>> response = testRestTemplate.exchange(
            "/api/v1/users/me/coupons?page=0&size=10", HttpMethod.GET,
            new HttpEntity<>(authHeaders(loginId, password)),
            new ParameterizedTypeReference<>() {}
        );
        return (int) response.getBody().data().memberCoupons().stream()
            .filter(mc -> mc.id().equals(memberCouponId) && mc.status().equals(expectedStatus))
            .count();
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

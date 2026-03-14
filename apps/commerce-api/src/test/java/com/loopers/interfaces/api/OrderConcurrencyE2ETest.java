package com.loopers.interfaces.api;

import com.loopers.interfaces.api.address.AddressV1Dto;
import com.loopers.interfaces.api.brand.BrandV1Dto;
import com.loopers.interfaces.api.member.MemberV1Dto;
import com.loopers.interfaces.api.order.OrderV1Dto;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderConcurrencyE2ETest {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final CacheManager cacheManager;

    @Autowired
    public OrderConcurrencyE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp, CacheManager cacheManager) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.cacheManager = cacheManager;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
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

            // Assert — 재고 확인 (별도 stock API)
            ResponseEntity<ApiResponse<ProductV1Dto.StockResponse>> stockResponse = testRestTemplate.exchange(
                "/api/v1/products/" + productId + "/stock", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            assertThat(successCount.get()).isEqualTo(threadCount);
            assertThat(stockResponse.getBody().data().stockQuantity()).isEqualTo(90);
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

            // Assert (별도 stock API)
            ResponseEntity<ApiResponse<ProductV1Dto.StockResponse>> stockResponse = testRestTemplate.exchange(
                "/api/v1/products/" + productId + "/stock", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            assertThat(successCount.get()).isEqualTo(5);
            assertThat(failCount.get()).isEqualTo(5);
            assertThat(stockResponse.getBody().data().stockQuantity()).isEqualTo(0);
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

            ResponseEntity<ApiResponse<ProductV1Dto.StockResponse>> p1Stock = testRestTemplate.exchange(
                "/api/v1/products/" + p1 + "/stock", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );
            ResponseEntity<ApiResponse<ProductV1Dto.StockResponse>> p2Stock = testRestTemplate.exchange(
                "/api/v1/products/" + p2 + "/stock", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            assertThat(p1Stock.getBody().data().stockQuantity()).isEqualTo(8);
            assertThat(p2Stock.getBody().data().stockQuantity()).isEqualTo(8);
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

            Long[][] productOrders = {
                {p1, p2, p3},
                {p3, p1, p2},
                {p2, p3, p1}
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

            ResponseEntity<ApiResponse<ProductV1Dto.StockResponse>> p1Stock = testRestTemplate.exchange(
                "/api/v1/products/" + p1 + "/stock", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );
            ResponseEntity<ApiResponse<ProductV1Dto.StockResponse>> p2Stock = testRestTemplate.exchange(
                "/api/v1/products/" + p2 + "/stock", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );
            ResponseEntity<ApiResponse<ProductV1Dto.StockResponse>> p3Stock = testRestTemplate.exchange(
                "/api/v1/products/" + p3 + "/stock", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            assertThat(p1Stock.getBody().data().stockQuantity()).isEqualTo(7);
            assertThat(p2Stock.getBody().data().stockQuantity()).isEqualTo(7);
            assertThat(p3Stock.getBody().data().stockQuantity()).isEqualTo(7);
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

            // Assert — 1번만 취소 성공, 재고 정확히 1개 복원 (별도 stock API)
            ResponseEntity<ApiResponse<ProductV1Dto.StockResponse>> stockResponse = testRestTemplate.exchange(
                "/api/v1/products/" + productId + "/stock", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failCount.get()).isEqualTo(1);
            assertThat(stockResponse.getBody().data().stockQuantity()).isEqualTo(100);
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

            // Assert — 재고 정합성: 초기 50 - 5(기존) + 5(취소 복원) - 5(신규) = 45 (별도 stock API)
            ResponseEntity<ApiResponse<ProductV1Dto.StockResponse>> stockResponse = testRestTemplate.exchange(
                "/api/v1/products/" + productId + "/stock", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            assertThat(createSuccess.get()).isEqualTo(5);
            assertThat(cancelSuccess.get()).isEqualTo(5);
            assertThat(stockResponse.getBody().data().stockQuantity()).isEqualTo(45);
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
            executor.awaitTermination(10, TimeUnit.SECONDS);

            // Assert — 주문 상태 확인
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> finalOrder = testRestTemplate.exchange(
                "/api/v1/orders/" + orderId, HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            String finalStatus = finalOrder.getBody().data().order().status();

            assertThat(cancelSuccess.get()).isEqualTo(1);
            assertThat(finalStatus).isEqualTo("CANCELLED");
            assertThat(updateSuccess.get()).isIn(0, 1);
        }
    }

    @DisplayName("재고 수정 + 주문 동시성 테스트")
    @Nested
    class StockUpdateAndOrderConcurrency {

        @DisplayName("관리자가 재고를 수정하는 동안 고객이 주문하면, 둘 다 정확히 반영된다.")
        @Test
        void concurrentStockUpdateAndOrder_bothAppliedCorrectly() throws InterruptedException {
            // Arrange
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 10000L, 100, 5);

            registerMember("stockUpdateUser", "Test1234!");
            Long addressId = registerAddress("stockUpdateUser", "Test1234!");

            int threadCount = 2;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger orderSuccess = new AtomicInteger(0);
            AtomicInteger stockUpdateSuccess = new AtomicInteger(0);

            // Act — 관리자 재고 수정(50)과 고객 주문(1개)을 동시에 실행
            executor.submit(() -> {
                try {
                    var request = new ProductV1Dto.UpdateStockRequest(50);
                    ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                        "/api-admin/v1/products/" + productId + "/stock", HttpMethod.PATCH,
                        adminEntity(request),
                        new ParameterizedTypeReference<>() {}
                    );
                    if (response.getStatusCode() == HttpStatus.OK) {
                        stockUpdateSuccess.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });

            executor.submit(() -> {
                try {
                    var items = List.of(new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 1));
                    var request = new OrderV1Dto.CreateOrderRequest(addressId, null, items);
                    ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                        "/api/v1/orders", HttpMethod.POST,
                        new HttpEntity<>(request, authHeaders("stockUpdateUser", "Test1234!")),
                        new ParameterizedTypeReference<>() {}
                    );
                    if (response.getStatusCode() == HttpStatus.OK) {
                        orderSuccess.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });

            latch.await();
            executor.shutdown();

            // Assert — 둘 다 성공하고, 최종 재고가 49 또는 50 (실행 순서에 따라 다름)
            assertThat(orderSuccess.get() + stockUpdateSuccess.get()).isEqualTo(2);

            ResponseEntity<ApiResponse<ProductV1Dto.StockResponse>> stockResponse = testRestTemplate.exchange(
                "/api/v1/products/" + productId + "/stock", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );
            int finalStock = stockResponse.getBody().data().stockQuantity();
            assertThat(finalStock).isIn(49, 50);
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

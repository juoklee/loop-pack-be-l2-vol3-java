package com.loopers.interfaces.api;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayResponse;
import com.loopers.interfaces.api.address.AddressV1Dto;
import com.loopers.interfaces.api.brand.BrandV1Dto;
import com.loopers.interfaces.api.member.MemberV1Dto;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.interfaces.api.payment.PaymentV1Dto;
import com.loopers.interfaces.api.product.ProductV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentV1ApiE2ETest {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    private static final String LOGIN_ID = "payuser";
    private static final String LOGIN_PW = "Test1234!";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final CacheManager cacheManager;

    @MockBean
    private PaymentGateway paymentGateway;

    @Autowired
    public PaymentV1ApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp,
                                CacheManager cacheManager) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.cacheManager = cacheManager;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @DisplayName("결제 Happy Path: 주문생성 → 결제생성 → 결제실행(PENDING) → 콜백(SUCCESS)")
    @Nested
    class HappyPath {

        @Test
        @DisplayName("결제 성공 시 Payment COMPLETED, Order COMPLETED")
        void fullPaymentFlow() {
            // Arrange
            registerMember();
            Long brandId = registerBrand();
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100);
            Long addressId = registerAddress();
            Long orderId = createOrderAndGetId(productId, 2);

            given(paymentGateway.requestPayment(anyLong(), anyString(), anyString(), anyString(), anyLong()))
                .willReturn(new PaymentGatewayResponse("txn-001", String.format("%06d", orderId), "PENDING", null));

            // Act 1: 결제 생성
            var createRequest = new PaymentV1Dto.CreatePaymentRequest(orderId, "SAMSUNG", "1234-5678-9012-3456");
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> createResponse = testRestTemplate.exchange(
                "/api/v1/payments",
                HttpMethod.POST,
                new HttpEntity<>(createRequest, authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(createResponse.getBody().data().payment().status()).isEqualTo("REQUESTED"),
                () -> assertThat(createResponse.getBody().data().payment().orderId()).isEqualTo(orderId)
            );

            Long paymentId = createResponse.getBody().data().payment().id();

            // Act 2: 결제 실행
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> executeResponse = testRestTemplate.exchange(
                "/api/v1/payments/" + paymentId + "/pay",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(executeResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(executeResponse.getBody().data().payment().status()).isEqualTo("PROCESSING"),
                () -> assertThat(executeResponse.getBody().data().payment().transactionKey()).isEqualTo("txn-001")
            );

            // Act 3: 콜백 (SUCCESS)
            var callbackRequest = new PaymentV1Dto.CallbackRequest(
                "txn-001", String.format("%06d", orderId), "SAMSUNG", "1234-5678-9012-3456", 278000L, "SUCCESS", null
            );
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> callbackResponse = testRestTemplate.exchange(
                "/api/internal/v1/payments/callback",
                HttpMethod.POST,
                new HttpEntity<>(callbackRequest),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(callbackResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(callbackResponse.getBody().data().payment().status()).isEqualTo("COMPLETED")
            );

            // Assert: Order도 COMPLETED
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> orderResponse = testRestTemplate.exchange(
                "/api/v1/orders/" + orderId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            assertThat(orderResponse.getBody().data().order().status()).isEqualTo("COMPLETED");
        }
    }

    @DisplayName("PG 요청 실패 (CircuitBreaker fallback)")
    @Nested
    class PgRequestFailure {

        @Test
        @DisplayName("PG null 반환 시 Payment FAILED, Order PAYMENT_FAILED, 재고 복원")
        void pgNullResponse_failsPaymentAndRestoresStock() {
            // Arrange
            registerMember();
            Long brandId = registerBrand();
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100);
            Long addressId = registerAddress();
            Long orderId = createOrderAndGetId(productId, 2);

            given(paymentGateway.requestPayment(anyLong(), anyString(), anyString(), anyString(), anyLong()))
                .willReturn(null);

            // Act: 결제 생성
            var createRequest = new PaymentV1Dto.CreatePaymentRequest(orderId, "SAMSUNG", "1234-5678-9012-3456");
            Long paymentId = testRestTemplate.exchange(
                "/api/v1/payments", HttpMethod.POST,
                new HttpEntity<>(createRequest, authHeaders()),
                new ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>>() {}
            ).getBody().data().payment().id();

            // Act: 결제 실행 (PG null → FAILED)
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> executeResponse = testRestTemplate.exchange(
                "/api/v1/payments/" + paymentId + "/pay",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(executeResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(executeResponse.getBody().data().payment().status()).isEqualTo("FAILED"),
                () -> assertThat(executeResponse.getBody().data().payment().failReason()).isEqualTo("PG 서비스 일시 장애")
            );

            // Order PAYMENT_FAILED
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> orderResponse = testRestTemplate.exchange(
                "/api/v1/orders/" + orderId, HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            assertThat(orderResponse.getBody().data().order().status()).isEqualTo("PAYMENT_FAILED");

            // 재고 복원 확인
            ResponseEntity<ApiResponse<ProductV1Dto.StockResponse>> stockResponse = testRestTemplate.exchange(
                "/api/v1/products/" + productId + "/stock", HttpMethod.GET,
                null, new ParameterizedTypeReference<>() {}
            );
            assertThat(stockResponse.getBody().data().stockQuantity()).isEqualTo(100);
        }
    }

    @DisplayName("콜백 실패 (한도 초과)")
    @Nested
    class CallbackFailure {

        @Test
        @DisplayName("콜백 FAILED 수신 시 Payment FAILED, Order PAYMENT_FAILED, 재고 복원")
        void failedCallback_restoresStockAndCoupon() {
            // Arrange
            registerMember();
            Long brandId = registerBrand();
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100);
            Long addressId = registerAddress();
            Long orderId = createOrderAndGetId(productId, 2);

            given(paymentGateway.requestPayment(anyLong(), anyString(), anyString(), anyString(), anyLong()))
                .willReturn(new PaymentGatewayResponse("txn-002", String.format("%06d", orderId), "PENDING", null));

            var createRequest = new PaymentV1Dto.CreatePaymentRequest(orderId, "SAMSUNG", "1234-5678-9012-3456");
            Long paymentId = testRestTemplate.exchange(
                "/api/v1/payments", HttpMethod.POST,
                new HttpEntity<>(createRequest, authHeaders()),
                new ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>>() {}
            ).getBody().data().payment().id();

            // 결제 실행 (PROCESSING)
            testRestTemplate.exchange(
                "/api/v1/payments/" + paymentId + "/pay", HttpMethod.POST,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>>() {}
            );

            // Act: 콜백 FAILED
            var callbackRequest = new PaymentV1Dto.CallbackRequest(
                "txn-002", String.format("%06d", orderId), "SAMSUNG", "1234-5678-9012-3456", 278000L, "FAILED", "한도 초과"
            );
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> callbackResponse = testRestTemplate.exchange(
                "/api/internal/v1/payments/callback", HttpMethod.POST,
                new HttpEntity<>(callbackRequest),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(callbackResponse.getBody().data().payment().status()).isEqualTo("FAILED"),
                () -> assertThat(callbackResponse.getBody().data().payment().failReason()).isEqualTo("한도 초과")
            );

            // Order PAYMENT_FAILED
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> orderResponse = testRestTemplate.exchange(
                "/api/v1/orders/" + orderId, HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            assertThat(orderResponse.getBody().data().order().status()).isEqualTo("PAYMENT_FAILED");

            // 재고 복원
            ResponseEntity<ApiResponse<ProductV1Dto.StockResponse>> stockResponse = testRestTemplate.exchange(
                "/api/v1/products/" + productId + "/stock", HttpMethod.GET,
                null, new ParameterizedTypeReference<>() {}
            );
            assertThat(stockResponse.getBody().data().stockQuantity()).isEqualTo(100);
        }
    }

    @DisplayName("중복 콜백 처리 (멱등)")
    @Nested
    class DuplicateCallback {

        @Test
        @DisplayName("동일 콜백 2회 수신 시 두 번째는 무시된다")
        void duplicateCallback_isIdempotent() {
            // Arrange
            registerMember();
            Long brandId = registerBrand();
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100);
            Long addressId = registerAddress();
            Long orderId = createOrderAndGetId(productId, 1);

            given(paymentGateway.requestPayment(anyLong(), anyString(), anyString(), anyString(), anyLong()))
                .willReturn(new PaymentGatewayResponse("txn-003", String.format("%06d", orderId), "PENDING", null));

            var createRequest = new PaymentV1Dto.CreatePaymentRequest(orderId, "KB", "1234-5678-9012-3456");
            Long paymentId = testRestTemplate.exchange(
                "/api/v1/payments", HttpMethod.POST,
                new HttpEntity<>(createRequest, authHeaders()),
                new ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>>() {}
            ).getBody().data().payment().id();

            testRestTemplate.exchange(
                "/api/v1/payments/" + paymentId + "/pay", HttpMethod.POST,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>>() {}
            );

            var callbackRequest = new PaymentV1Dto.CallbackRequest(
                "txn-003", String.format("%06d", orderId), "KB", "1234-5678-9012-3456", 139000L, "SUCCESS", null
            );

            // Act: 첫 번째 콜백
            testRestTemplate.exchange(
                "/api/internal/v1/payments/callback", HttpMethod.POST,
                new HttpEntity<>(callbackRequest),
                new ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>>() {}
            );

            // Act: 두 번째 콜백 (동일)
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> secondResponse = testRestTemplate.exchange(
                "/api/internal/v1/payments/callback", HttpMethod.POST,
                new HttpEntity<>(callbackRequest),
                new ParameterizedTypeReference<>() {}
            );

            // Assert: 여전히 COMPLETED (무시됨)
            assertThat(secondResponse.getBody().data().payment().status()).isEqualTo("COMPLETED");
        }
    }

    @DisplayName("동시 결제 실행 방지")
    @Nested
    class ConcurrentExecutePayment {

        @Test
        @DisplayName("동일 결제를 동시에 실행하면 하나만 PG 호출에 성공하고 나머지는 거부된다")
        void onlyOnePgCallOnConcurrentExecution() throws Exception {
            // Arrange
            registerMember();
            Long brandId = registerBrand();
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100);
            Long addressId = registerAddress();
            Long orderId = createOrderAndGetId(productId, 1);

            given(paymentGateway.requestPayment(anyLong(), anyString(), anyString(), anyString(), anyLong()))
                .willReturn(new PaymentGatewayResponse("txn-concurrent", String.format("%06d", orderId), "PENDING", null));

            var createRequest = new PaymentV1Dto.CreatePaymentRequest(orderId, "SAMSUNG", "1234-5678-9012-3456");
            Long paymentId = testRestTemplate.exchange(
                "/api/v1/payments", HttpMethod.POST,
                new HttpEntity<>(createRequest, authHeaders()),
                new ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>>() {}
            ).getBody().data().payment().id();

            // Act: 5개 스레드에서 동시에 결제 실행
            int threadCount = 5;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(1);
            List<Future<ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>>>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    latch.await();
                    return testRestTemplate.exchange(
                        "/api/v1/payments/" + paymentId + "/pay",
                        HttpMethod.POST,
                        new HttpEntity<>(authHeaders()),
                        new ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>>() {}
                    );
                }));
            }

            latch.countDown();

            List<ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>>> results = new ArrayList<>();
            for (Future<ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>>> future : futures) {
                results.add(future.get());
            }
            executor.shutdown();

            // Assert: PG 호출은 정확히 1번만 발생
            verify(paymentGateway, times(1))
                .requestPayment(anyLong(), anyString(), anyString(), anyString(), anyLong());

            // Assert: 성공 응답은 정확히 1건
            long successCount = results.stream()
                .filter(r -> r.getStatusCode() == HttpStatus.OK)
                .filter(r -> {
                    var data = r.getBody().data();
                    return data != null && data.payment() != null
                        && "PROCESSING".equals(data.payment().status());
                })
                .count();
            assertThat(successCount).isEqualTo(1);
        }
    }

    @DisplayName("결제 조회")
    @Nested
    class GetPayment {

        @Test
        @DisplayName("결제 생성 후 조회하면 REQUESTED 상태로 반환된다")
        void getPayment_afterCreate() {
            // Arrange
            registerMember();
            Long brandId = registerBrand();
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100);
            Long addressId = registerAddress();
            Long orderId = createOrderAndGetId(productId, 1);

            var createRequest = new PaymentV1Dto.CreatePaymentRequest(orderId, "HYUNDAI", "1234-5678-9012-3456");
            Long paymentId = testRestTemplate.exchange(
                "/api/v1/payments", HttpMethod.POST,
                new HttpEntity<>(createRequest, authHeaders()),
                new ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>>() {}
            ).getBody().data().payment().id();

            // Act
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                "/api/v1/payments/" + paymentId, HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().payment().status()).isEqualTo("REQUESTED"),
                () -> assertThat(response.getBody().data().payment().cardType()).isEqualTo("HYUNDAI"),
                () -> assertThat(response.getBody().data().payment().amount()).isEqualTo(139000L)
            );
        }
    }

    @DisplayName("인증 실패")
    @Nested
    class Unauthorized {

        @Test
        @DisplayName("인증 헤더 없이 결제 API 호출 시 401 반환")
        void noAuth_returns401() {
            var createRequest = new PaymentV1Dto.CreatePaymentRequest(1L, "SAMSUNG", "1234-5678-9012-3456");
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                "/api/v1/payments", HttpMethod.POST,
                new HttpEntity<>(createRequest),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // ===== Helper Methods =====

    private void registerMember() {
        var request = new MemberV1Dto.RegisterRequest(
            LOGIN_ID, LOGIN_PW, "결제테스트", LocalDate.of(1990, 1, 15),
            "MALE", LOGIN_ID + "@example.com", null
        );
        testRestTemplate.exchange(
            "/api/v1/members", HttpMethod.POST,
            new HttpEntity<>(request),
            new ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {}
        );
    }

    private Long registerBrand() {
        var request = new BrandV1Dto.RegisterRequest("Nike", "Just Do It");
        ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
            "/api-admin/v1/brands", HttpMethod.POST,
            adminEntity(request),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().brand().id();
    }

    private Long registerProduct(Long brandId, String name, Long price, int stock) {
        var request = new ProductV1Dto.RegisterRequest(brandId, name, "설명", price, stock, 10);
        ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
            "/api-admin/v1/products", HttpMethod.POST,
            adminEntity(request),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().product().id();
    }

    private Long registerAddress() {
        var request = new AddressV1Dto.CreateAddressRequest(
            "집", "홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호"
        );
        ResponseEntity<ApiResponse<AddressV1Dto.AddressResponse>> response = testRestTemplate.exchange(
            "/api/v1/members/me/addresses", HttpMethod.POST,
            new HttpEntity<>(request, authHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().address().id();
    }

    private Long createOrderAndGetId(Long productId, int quantity) {
        Long addressId = registerAddress();
        var items = List.of(new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, quantity));
        var request = new OrderV1Dto.CreateOrderRequest(addressId, null, items);
        ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
            "/api/v1/orders", HttpMethod.POST,
            new HttpEntity<>(request, authHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().order().id();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, LOGIN_ID);
        headers.set(HEADER_LOGIN_PW, LOGIN_PW);
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

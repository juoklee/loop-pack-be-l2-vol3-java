package com.loopers.interfaces.api;

import com.loopers.interfaces.api.address.AddressV1Dto;
import com.loopers.interfaces.api.brand.BrandV1Dto;
import com.loopers.interfaces.api.coupon.CouponV1Dto;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public OrderV1ApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/orders (주문 생성)")
    @Nested
    class CreateOrder {

        @DisplayName("단일 상품 주문 시, 주문 정보와 재고 차감을 확인한다.")
        @Test
        void createsOrder_withSingleItem() {
            // Arrange
            registerMember("user1", "Test1234!");
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);
            Long addressId = registerAddress("user1", "Test1234!");

            var items = List.of(new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 2));
            var request = new OrderV1Dto.CreateOrderRequest(addressId, null, items);

            // Act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().order().totalAmount()).isEqualTo(278000L),
                () -> assertThat(response.getBody().data().order().status()).isEqualTo("COMPLETED"),
                () -> assertThat(response.getBody().data().order().items()).hasSize(1),
                () -> assertThat(response.getBody().data().order().items().get(0).quantity()).isEqualTo(2),
                () -> assertThat(response.getBody().data().order().recipientName()).isEqualTo("홍길동")
            );

            // 재고 차감 확인
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> productResponse = testRestTemplate.exchange(
                "/api/v1/products/" + productId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );
            assertThat(productResponse.getBody().data().product().stockQuantity()).isEqualTo(98);
        }

        @DisplayName("다수 상품 주문 시, 각 상품의 주문 항목이 생성된다.")
        @Test
        void createsOrder_withMultipleItems() {
            // Arrange
            registerMember("user1", "Test1234!");
            Long brandId = registerBrand("Nike", "Just Do It");
            Long product1 = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);
            Long product2 = registerProduct(brandId, "에어포스 1", 119000L, 50, 3);
            Long addressId = registerAddress("user1", "Test1234!");

            var items = List.of(
                new OrderV1Dto.CreateOrderRequest.OrderItemRequest(product1, 1),
                new OrderV1Dto.CreateOrderRequest.OrderItemRequest(product2, 2)
            );
            var request = new OrderV1Dto.CreateOrderRequest(addressId, null, items);

            // Act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().order().totalAmount()).isEqualTo(377000L),
                () -> assertThat(response.getBody().data().order().items()).hasSize(2)
            );
        }

        @DisplayName("동일 상품 중복 요청 시, 수량이 합산된다.")
        @Test
        void mergesDuplicateItems() {
            // Arrange
            registerMember("user1", "Test1234!");
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);
            Long addressId = registerAddress("user1", "Test1234!");

            var items = List.of(
                new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 2),
                new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 3)
            );
            var request = new OrderV1Dto.CreateOrderRequest(addressId, null, items);

            // Act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().order().items()).hasSize(1),
                () -> assertThat(response.getBody().data().order().items().get(0).quantity()).isEqualTo(5),
                () -> assertThat(response.getBody().data().order().totalAmount()).isEqualTo(695000L)
            );
        }

        @DisplayName("재고 부족 시, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenInsufficientStock() {
            // Arrange
            registerMember("user1", "Test1234!");
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 3, 5);
            Long addressId = registerAddress("user1", "Test1234!");

            var items = List.of(new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 4));
            var request = new OrderV1Dto.CreateOrderRequest(addressId, null, items);

            // Act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            // 재고 롤백 확인
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> productResponse = testRestTemplate.exchange(
                "/api/v1/products/" + productId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );
            assertThat(productResponse.getBody().data().product().stockQuantity()).isEqualTo(3);
        }

        @DisplayName("maxOrderQuantity 초과 시, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenExceedsMaxOrderQuantity() {
            // Arrange
            registerMember("user1", "Test1234!");
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 3);
            Long addressId = registerAddress("user1", "Test1234!");

            var items = List.of(new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 4));
            var request = new OrderV1Dto.CreateOrderRequest(addressId, null, items);

            // Act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 배송지로 주문 시, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenAddressNotExists() {
            // Arrange
            registerMember("user1", "Test1234!");
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);

            var items = List.of(new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 1));
            var request = new OrderV1Dto.CreateOrderRequest(9999L, null, items);

            // Act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("빈 items로 주문 시, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenEmptyItems() {
            // Arrange
            registerMember("user1", "Test1234!");
            Long addressId = registerAddress("user1", "Test1234!");

            var request = new OrderV1Dto.CreateOrderRequest(addressId, null, List.of());

            // Act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("quantity가 0 이하이면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenQuantityZeroOrNegative() {
            // Arrange
            registerMember("user1", "Test1234!");
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);
            Long addressId = registerAddress("user1", "Test1234!");

            var items = List.of(new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 0));
            var request = new OrderV1Dto.CreateOrderRequest(addressId, null, items);

            // Act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("인증 없이 접근하면, 401 Unauthorized를 반환한다.")
        @Test
        void returnsUnauthorized_whenNoAuth() {
            // Act
            var request = new OrderV1Dto.CreateOrderRequest(1L, null, List.of());
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api/v1/orders (내 주문 목록)")
    @Nested
    class GetMyOrders {

        @DisplayName("날짜 범위 내 주문만 반환한다.")
        @Test
        void returnsOrdersWithinDateRange() {
            // Arrange
            registerMember("user1", "Test1234!");
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);
            Long addressId = registerAddress("user1", "Test1234!");
            HttpHeaders headers = authHeaders("user1", "Test1234!");

            createOrder(headers, addressId, List.of(
                new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 1)
            ));
            createOrder(headers, addressId, List.of(
                new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 2)
            ));

            LocalDate today = LocalDate.now();

            // Act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderListResponse>> response = testRestTemplate.exchange(
                "/api/v1/orders?startAt=" + today + "&endAt=" + today + "&page=0&size=20",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().orders()).hasSize(2)
            );
        }

        @DisplayName("타인의 주문은 조회되지 않는다.")
        @Test
        void excludesOtherMemberOrders() {
            // Arrange
            registerMember("user1", "Test1234!");
            registerMember("user2", "Test1234!");
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);
            Long address1 = registerAddress("user1", "Test1234!");
            Long address2 = registerAddress("user2", "Test1234!");

            createOrder(authHeaders("user1", "Test1234!"), address1, List.of(
                new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 1)
            ));
            createOrder(authHeaders("user2", "Test1234!"), address2, List.of(
                new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 1)
            ));

            LocalDate today = LocalDate.now();

            // Act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderListResponse>> response = testRestTemplate.exchange(
                "/api/v1/orders?startAt=" + today + "&endAt=" + today + "&page=0&size=20",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getBody().data().orders()).hasSize(1);
        }
    }

    @DisplayName("GET /api/v1/orders/{orderId} (주문 상세)")
    @Nested
    class GetOrder {

        @DisplayName("본인 주문 상세를 조회한다.")
        @Test
        void returnsOrderDetail() {
            // Arrange
            registerMember("user1", "Test1234!");
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);
            Long addressId = registerAddress("user1", "Test1234!");
            HttpHeaders headers = authHeaders("user1", "Test1234!");

            Long orderId = createOrderAndGetId(headers, addressId, List.of(
                new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 2)
            ));

            // Act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                "/api/v1/orders/" + orderId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().order().id()).isEqualTo(orderId),
                () -> assertThat(response.getBody().data().order().totalAmount()).isEqualTo(278000L),
                () -> assertThat(response.getBody().data().order().items()).hasSize(1)
            );
        }

        @DisplayName("타인의 주문을 조회하면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenOtherMemberOrder() {
            // Arrange
            registerMember("user1", "Test1234!");
            registerMember("user2", "Test1234!");
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);
            Long addressId = registerAddress("user1", "Test1234!");

            Long orderId = createOrderAndGetId(authHeaders("user1", "Test1234!"), addressId, List.of(
                new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 1)
            ));

            // Act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                "/api/v1/orders/" + orderId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("user2", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("POST /api/v1/orders/{orderId}/cancel (주문 취소)")
    @Nested
    class CancelOrder {

        @DisplayName("주문 취소 시, 상태가 CANCELLED로 변경되고 재고가 복원된다.")
        @Test
        void cancelsOrder_andRestoresStock() {
            // Arrange
            registerMember("user1", "Test1234!");
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);
            Long addressId = registerAddress("user1", "Test1234!");
            HttpHeaders headers = authHeaders("user1", "Test1234!");

            Long orderId = createOrderAndGetId(headers, addressId, List.of(
                new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 3)
            ));

            // Act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                "/api/v1/orders/" + orderId + "/cancel",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 상태 확인
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> orderResponse = testRestTemplate.exchange(
                "/api/v1/orders/" + orderId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );
            assertThat(orderResponse.getBody().data().order().status()).isEqualTo("CANCELLED");

            // 재고 복원 확인
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> productResponse = testRestTemplate.exchange(
                "/api/v1/products/" + productId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );
            assertThat(productResponse.getBody().data().product().stockQuantity()).isEqualTo(100);
        }

        @DisplayName("이미 취소된 주문을 취소하면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenAlreadyCancelled() {
            // Arrange
            registerMember("user1", "Test1234!");
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);
            Long addressId = registerAddress("user1", "Test1234!");
            HttpHeaders headers = authHeaders("user1", "Test1234!");

            Long orderId = createOrderAndGetId(headers, addressId, List.of(
                new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 1)
            ));

            // 첫 번째 취소
            testRestTemplate.exchange(
                "/api/v1/orders/" + orderId + "/cancel",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<ApiResponse<Object>>() {}
            );

            // Act - 두 번째 취소
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                "/api/v1/orders/" + orderId + "/cancel",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("PUT /api/v1/orders/{orderId}/shipping-address (배송지 수정)")
    @Nested
    class UpdateShippingAddress {

        @DisplayName("정상적으로 배송지를 수정한다.")
        @Test
        void updatesShippingAddress() {
            // Arrange
            registerMember("user1", "Test1234!");
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);
            Long addressId = registerAddress("user1", "Test1234!");
            HttpHeaders headers = authHeaders("user1", "Test1234!");

            Long orderId = createOrderAndGetId(headers, addressId, List.of(
                new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 1)
            ));

            var updateRequest = new OrderV1Dto.UpdateShippingAddressRequest(
                "김철수", "010-9999-9999", "54321", "서울시 서초구", "202호"
            );

            // Act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                "/api/v1/orders/" + orderId + "/shipping-address",
                HttpMethod.PUT,
                new HttpEntity<>(updateRequest, headers),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().order().recipientName()).isEqualTo("김철수"),
                () -> assertThat(response.getBody().data().order().recipientPhone()).isEqualTo("010-9999-9999"),
                () -> assertThat(response.getBody().data().order().zipCode()).isEqualTo("54321")
            );
        }

        @DisplayName("취소된 주문의 배송지를 수정하면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenOrderCancelled() {
            // Arrange
            registerMember("user1", "Test1234!");
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);
            Long addressId = registerAddress("user1", "Test1234!");
            HttpHeaders headers = authHeaders("user1", "Test1234!");

            Long orderId = createOrderAndGetId(headers, addressId, List.of(
                new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 1)
            ));

            // 주문 취소
            testRestTemplate.exchange(
                "/api/v1/orders/" + orderId + "/cancel",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<ApiResponse<Object>>() {}
            );

            var updateRequest = new OrderV1Dto.UpdateShippingAddressRequest(
                "김철수", "010-9999-9999", "54321", "서울시 서초구", "202호"
            );

            // Act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                "/api/v1/orders/" + orderId + "/shipping-address",
                HttpMethod.PUT,
                new HttpEntity<>(updateRequest, headers),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("Admin API")
    @Nested
    class AdminApi {

        @DisplayName("GET /api-admin/v1/orders - 전체 주문 목록을 조회한다.")
        @Test
        void returnsAllOrders() {
            // Arrange
            registerMember("user1", "Test1234!");
            registerMember("user2", "Test1234!");
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);
            Long address1 = registerAddress("user1", "Test1234!");
            Long address2 = registerAddress("user2", "Test1234!");

            createOrder(authHeaders("user1", "Test1234!"), address1, List.of(
                new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 1)
            ));
            createOrder(authHeaders("user2", "Test1234!"), address2, List.of(
                new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 1)
            ));

            // Act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderListResponse>> response = testRestTemplate.exchange(
                "/api-admin/v1/orders?page=0&size=20",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().orders()).hasSize(2)
            );
        }

        @DisplayName("GET /api-admin/v1/orders?memberId= - memberId로 필터링한다.")
        @Test
        void filtersOrdersByMemberId() {
            // Arrange
            registerMember("user1", "Test1234!");
            registerMember("user2", "Test1234!");
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);
            Long address1 = registerAddress("user1", "Test1234!");
            Long address2 = registerAddress("user2", "Test1234!");

            Long orderId = createOrderAndGetId(authHeaders("user1", "Test1234!"), address1, List.of(
                new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 1)
            ));
            createOrder(authHeaders("user2", "Test1234!"), address2, List.of(
                new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 1)
            ));

            // user1의 memberId 추출
            Long memberId = getOrderMemberId(orderId);

            // Act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderListResponse>> response = testRestTemplate.exchange(
                "/api-admin/v1/orders?memberId=" + memberId + "&page=0&size=20",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().orders()).hasSize(1)
            );
        }

        @DisplayName("GET /api-admin/v1/orders/{orderId} - 주문 상세를 조회한다.")
        @Test
        void returnsOrderDetail() {
            // Arrange
            registerMember("user1", "Test1234!");
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);
            Long addressId = registerAddress("user1", "Test1234!");

            Long orderId = createOrderAndGetId(authHeaders("user1", "Test1234!"), addressId, List.of(
                new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 2)
            ));

            // Act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                "/api-admin/v1/orders/" + orderId,
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().order().id()).isEqualTo(orderId),
                () -> assertThat(response.getBody().data().order().totalAmount()).isEqualTo(278000L)
            );
        }
    }

    @DisplayName("POST /api/v1/orders (쿠폰 적용 주문)")
    @Nested
    class CreateOrderWithCoupon {

        private static final LocalDateTime FUTURE = LocalDateTime.now().plusDays(30);

        @DisplayName("정액 쿠폰 적용 시, 할인 금액만큼 차감된 totalAmount로 주문이 생성된다.")
        @Test
        void createsOrder_withFixedCoupon() {
            // Arrange
            registerMember("user1", "Test1234!");
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);
            Long addressId = registerAddress("user1", "Test1234!");
            Long couponId = createCoupon("5000원 할인", "FIXED", 5000L, null, FUTURE);
            Long memberCouponId = issueCouponAndGetId("user1", "Test1234!", couponId);

            var items = List.of(new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 2));
            var request = new OrderV1Dto.CreateOrderRequest(addressId, memberCouponId, items);

            // Act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                "/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().order().totalAmount()).isEqualTo(273000L),
                () -> assertThat(response.getBody().data().order().originalAmount()).isEqualTo(278000L),
                () -> assertThat(response.getBody().data().order().discountAmount()).isEqualTo(5000L),
                () -> assertThat(response.getBody().data().order().memberCouponId()).isEqualTo(memberCouponId)
            );
        }

        @DisplayName("정률 쿠폰 적용 시, 주문 금액 비율로 할인된다.")
        @Test
        void createsOrder_withRateCoupon() {
            // Arrange
            registerMember("user1", "Test1234!");
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 100000L, 100, 5);
            Long addressId = registerAddress("user1", "Test1234!");
            Long couponId = createCoupon("10% 할인", "RATE", 10L, null, FUTURE);
            Long memberCouponId = issueCouponAndGetId("user1", "Test1234!", couponId);

            var items = List.of(new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 1));
            var request = new OrderV1Dto.CreateOrderRequest(addressId, memberCouponId, items);

            // Act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                "/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().order().totalAmount()).isEqualTo(90000L),
                () -> assertThat(response.getBody().data().order().originalAmount()).isEqualTo(100000L),
                () -> assertThat(response.getBody().data().order().discountAmount()).isEqualTo(10000L)
            );
        }

        @DisplayName("존재하지 않는 memberCouponId로 주문하면, 404를 반환한다.")
        @Test
        void returnsNotFound_whenMemberCouponNotExists() {
            // Arrange
            registerMember("user1", "Test1234!");
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);
            Long addressId = registerAddress("user1", "Test1234!");

            var items = List.of(new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 1));
            var request = new OrderV1Dto.CreateOrderRequest(addressId, 9999L, items);

            // Act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                "/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

            // 재고 롤백 확인
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> productResponse = testRestTemplate.exchange(
                "/api/v1/products/" + productId, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );
            assertThat(productResponse.getBody().data().product().stockQuantity()).isEqualTo(100);
        }

        @DisplayName("이미 사용된 쿠폰으로 주문하면, 400을 반환하고 재고가 롤백된다.")
        @Test
        void returnsBadRequest_whenCouponAlreadyUsed() {
            // Arrange
            registerMember("user1", "Test1234!");
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);
            Long addressId = registerAddress("user1", "Test1234!");
            Long couponId = createCoupon("쿠폰", "FIXED", 5000L, null, FUTURE);
            Long memberCouponId = issueCouponAndGetId("user1", "Test1234!", couponId);

            // 첫 번째 주문으로 쿠폰 사용
            var items1 = List.of(new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 1));
            var request1 = new OrderV1Dto.CreateOrderRequest(addressId, memberCouponId, items1);
            testRestTemplate.exchange(
                "/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(request1, authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {}
            );

            // Act — 같은 쿠폰으로 두 번째 주문
            var items2 = List.of(new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 1));
            var request2 = new OrderV1Dto.CreateOrderRequest(addressId, memberCouponId, items2);
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                "/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(request2, authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            // 재고 롤백 확인 (첫 주문으로 1개 차감 → 99개여야 함)
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> productResponse = testRestTemplate.exchange(
                "/api/v1/products/" + productId, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );
            assertThat(productResponse.getBody().data().product().stockQuantity()).isEqualTo(99);
        }

        @DisplayName("최소 주문 금액 미달 시, 400을 반환한다.")
        @Test
        void returnsBadRequest_whenBelowMinOrderAmount() {
            // Arrange
            registerMember("user1", "Test1234!");
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 10000L, 100, 5);
            Long addressId = registerAddress("user1", "Test1234!");
            Long couponId = createCoupon("쿠폰", "FIXED", 5000L, 50000L, FUTURE);
            Long memberCouponId = issueCouponAndGetId("user1", "Test1234!", couponId);

            var items = List.of(new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 1));
            var request = new OrderV1Dto.CreateOrderRequest(addressId, memberCouponId, items);

            // Act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                "/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("타인의 쿠폰으로 주문하면, 400을 반환한다.")
        @Test
        void returnsBadRequest_whenNotCouponOwner() {
            // Arrange
            registerMember("user1", "Test1234!");
            registerMember("user2", "Test1234!");
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);
            Long addressId1 = registerAddress("user1", "Test1234!");
            Long couponId = createCoupon("쿠폰", "FIXED", 5000L, null, FUTURE);
            Long memberCouponId = issueCouponAndGetId("user2", "Test1234!", couponId);

            var items = List.of(new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 1));
            var request = new OrderV1Dto.CreateOrderRequest(addressId1, memberCouponId, items);

            // Act — user1이 user2의 쿠폰으로 주문
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                "/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("쿠폰 적용 주문 취소 시, 쿠폰이 복원되고 재고가 복원된다.")
        @Test
        void restoresCouponAndStock_whenCouponOrderCancelled() {
            // Arrange
            registerMember("user1", "Test1234!");
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);
            Long addressId = registerAddress("user1", "Test1234!");
            Long couponId = createCoupon("5000원 할인", "FIXED", 5000L, null, FUTURE);
            Long memberCouponId = issueCouponAndGetId("user1", "Test1234!", couponId);
            HttpHeaders headers = authHeaders("user1", "Test1234!");

            // 쿠폰 적용 주문 생성
            var items = List.of(new OrderV1Dto.CreateOrderRequest.OrderItemRequest(productId, 2));
            var request = new OrderV1Dto.CreateOrderRequest(addressId, memberCouponId, items);
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> orderResponse = testRestTemplate.exchange(
                "/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
            );
            Long orderId = orderResponse.getBody().data().order().id();

            // Act — 주문 취소
            testRestTemplate.exchange(
                "/api/v1/orders/" + orderId + "/cancel", HttpMethod.POST,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<ApiResponse<Object>>() {}
            );

            // Assert — 재고 복원 확인
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> productResponse = testRestTemplate.exchange(
                "/api/v1/products/" + productId, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );
            assertThat(productResponse.getBody().data().product().stockQuantity()).isEqualTo(100);

            // Assert — 쿠폰 복원 확인 (AVAILABLE 상태)
            ResponseEntity<ApiResponse<CouponV1Dto.MemberCouponListResponse>> couponResponse = testRestTemplate.exchange(
                "/api/v1/users/me/coupons", HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );
            assertThat(couponResponse.getBody().data().memberCoupons().get(0).status()).isEqualTo("AVAILABLE");
        }
    }

    // --- Helper Methods ---

    private void registerMember(String loginId, String password) {
        var request = new MemberV1Dto.RegisterRequest(
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

    private Long registerAddress(String loginId, String password) {
        var request = new AddressV1Dto.CreateAddressRequest(
            "집", "홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호"
        );
        ResponseEntity<ApiResponse<AddressV1Dto.AddressResponse>> response = testRestTemplate.exchange(
            "/api/v1/members/me/addresses",
            HttpMethod.POST,
            new HttpEntity<>(request, authHeaders(loginId, password)),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().address().id();
    }

    private void createOrder(HttpHeaders headers, Long addressId,
                             List<OrderV1Dto.CreateOrderRequest.OrderItemRequest> items) {
        var request = new OrderV1Dto.CreateOrderRequest(addressId, null, items);
        testRestTemplate.exchange(
            "/api/v1/orders",
            HttpMethod.POST,
            new HttpEntity<>(request, headers),
            new ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {}
        );
    }

    private Long createOrderAndGetId(HttpHeaders headers, Long addressId,
                                     List<OrderV1Dto.CreateOrderRequest.OrderItemRequest> items) {
        var request = new OrderV1Dto.CreateOrderRequest(addressId, null, items);
        ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
            "/api/v1/orders",
            HttpMethod.POST,
            new HttpEntity<>(request, headers),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().order().id();
    }

    private Long getOrderMemberId(Long orderId) {
        ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
            "/api-admin/v1/orders/" + orderId,
            HttpMethod.GET,
            new HttpEntity<>(adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().order().memberId();
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

    private Long createCoupon(String name, String type, Long value, Long minOrderAmount, LocalDateTime expiredAt) {
        var request = new CouponV1Dto.CreateCouponRequest(name, type, value, minOrderAmount, expiredAt, null, null);
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
}

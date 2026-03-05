package com.loopers.interfaces.api;

import com.loopers.interfaces.api.coupon.CouponV1Dto;
import com.loopers.interfaces.api.member.MemberV1Dto;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponAdminV1ApiE2ETest {

    private static final String COUPON_ADMIN = "/api-admin/v1/coupons";
    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    private static final LocalDateTime FUTURE = LocalDateTime.now().plusDays(30);

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public CouponAdminV1ApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api-admin/v1/coupons (쿠폰 등록)")
    @Nested
    class CreateCoupon {

        @DisplayName("정액 쿠폰을 등록하면, 201 Created 응답을 받는다.")
        @Test
        void returnsCreated_whenFixedCoupon() {
            // Arrange
            var request = new CouponV1Dto.CreateCouponRequest("5000원 할인", "FIXED", 5000L, null, FUTURE, null, null);

            // Act
            ResponseEntity<ApiResponse<CouponV1Dto.CouponResponse>> response = testRestTemplate.exchange(
                COUPON_ADMIN, HttpMethod.POST, adminEntity(request),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().data().coupon().name()).isEqualTo("5000원 할인"),
                () -> assertThat(response.getBody().data().coupon().type()).isEqualTo("FIXED"),
                () -> assertThat(response.getBody().data().coupon().value()).isEqualTo(5000L)
            );
        }

        @DisplayName("정률 쿠폰을 등록하면, 201 Created 응답을 받는다.")
        @Test
        void returnsCreated_whenRateCoupon() {
            // Arrange
            var request = new CouponV1Dto.CreateCouponRequest("10% 할인", "RATE", 10L, 30000L, FUTURE, null, null);

            // Act
            ResponseEntity<ApiResponse<CouponV1Dto.CouponResponse>> response = testRestTemplate.exchange(
                COUPON_ADMIN, HttpMethod.POST, adminEntity(request),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().data().coupon().type()).isEqualTo("RATE"),
                () -> assertThat(response.getBody().data().coupon().value()).isEqualTo(10L),
                () -> assertThat(response.getBody().data().coupon().minOrderAmount()).isEqualTo(30000L)
            );
        }

        @DisplayName("이름이 빈 문자열이면, 400 Bad Request 응답을 받는다.")
        @Test
        void returnsBadRequest_whenNameIsBlank() {
            var request = new CouponV1Dto.CreateCouponRequest("  ", "FIXED", 5000L, null, FUTURE, null, null);

            ResponseEntity<ApiResponse<CouponV1Dto.CouponResponse>> response = testRestTemplate.exchange(
                COUPON_ADMIN, HttpMethod.POST, adminEntity(request),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("수량 제한 쿠폰을 등록하면, totalQuantity와 issuedQuantity가 응답에 포함된다.")
        @Test
        void returnsCreated_whenLimitedCoupon() {
            // Arrange
            var request = new CouponV1Dto.CreateCouponRequest("선착순 쿠폰", "FIXED", 3000L, null, FUTURE, null, 100);

            // Act
            ResponseEntity<ApiResponse<CouponV1Dto.CouponResponse>> response = testRestTemplate.exchange(
                COUPON_ADMIN, HttpMethod.POST, adminEntity(request),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().data().coupon().totalQuantity()).isEqualTo(100),
                () -> assertThat(response.getBody().data().coupon().issuedQuantity()).isEqualTo(0)
            );
        }

        @DisplayName("validDays가 설정된 기간제 쿠폰을 등록하면, 응답에 validDays가 포함된다.")
        @Test
        void returnsCreated_whenValidDaysCoupon() {
            // Arrange
            var request = new CouponV1Dto.CreateCouponRequest("기간제 쿠폰", "FIXED", 5000L, null, FUTURE, 7, null);

            // Act
            ResponseEntity<ApiResponse<CouponV1Dto.CouponResponse>> response = testRestTemplate.exchange(
                COUPON_ADMIN, HttpMethod.POST, adminEntity(request),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().data().coupon().validDays()).isEqualTo(7),
                () -> assertThat(response.getBody().data().coupon().totalQuantity()).isNull()
            );
        }

        @DisplayName("정률 쿠폰의 할인율이 100을 초과하면, 400 Bad Request 응답을 받는다.")
        @Test
        void returnsBadRequest_whenRateExceeds100() {
            var request = new CouponV1Dto.CreateCouponRequest("쿠폰", "RATE", 101L, null, FUTURE, null, null);

            ResponseEntity<ApiResponse<CouponV1Dto.CouponResponse>> response = testRestTemplate.exchange(
                COUPON_ADMIN, HttpMethod.POST, adminEntity(request),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api-admin/v1/coupons (쿠폰 목록 조회)")
    @Nested
    class GetCoupons {

        @DisplayName("쿠폰이 존재하면, 목록을 반환한다.")
        @Test
        void returnsCoupons_whenCouponsExist() {
            // Arrange
            createCoupon("쿠폰1", "FIXED", 5000L);
            createCoupon("쿠폰2", "RATE", 10L);

            // Act
            ResponseEntity<ApiResponse<CouponV1Dto.CouponListResponse>> response = testRestTemplate.exchange(
                COUPON_ADMIN, HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().coupons()).hasSize(2)
            );
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{couponId} (쿠폰 상세 조회)")
    @Nested
    class GetCoupon {

        @DisplayName("존재하는 쿠폰을 조회하면, 200 OK 응답을 받는다.")
        @Test
        void returnsOk_whenCouponExists() {
            Long couponId = createCoupon("5000원 할인", "FIXED", 5000L);

            ResponseEntity<ApiResponse<CouponV1Dto.CouponResponse>> response = testRestTemplate.exchange(
                COUPON_ADMIN + "/" + couponId, HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().coupon().name()).isEqualTo("5000원 할인")
            );
        }

        @DisplayName("존재하지 않는 쿠폰을 조회하면, 404 Not Found 응답을 받는다.")
        @Test
        void returnsNotFound_whenCouponNotExists() {
            ResponseEntity<ApiResponse<CouponV1Dto.CouponResponse>> response = testRestTemplate.exchange(
                COUPON_ADMIN + "/999", HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("PUT /api-admin/v1/coupons/{couponId} (쿠폰 수정)")
    @Nested
    class UpdateCoupon {

        @DisplayName("유효한 정보로 수정하면, 200 OK 응답을 받는다.")
        @Test
        void returnsOk_whenValidRequest() {
            Long couponId = createCoupon("기존 쿠폰", "FIXED", 5000L);
            var request = new CouponV1Dto.UpdateCouponRequest("수정된 쿠폰", 3000L, 20000L, FUTURE.plusDays(10), null);

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                COUPON_ADMIN + "/" + couponId, HttpMethod.PUT, adminEntity(request),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 수정 확인
            ResponseEntity<ApiResponse<CouponV1Dto.CouponResponse>> getResponse = testRestTemplate.exchange(
                COUPON_ADMIN + "/" + couponId, HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            assertAll(
                () -> assertThat(getResponse.getBody().data().coupon().name()).isEqualTo("수정된 쿠폰"),
                () -> assertThat(getResponse.getBody().data().coupon().value()).isEqualTo(3000L),
                () -> assertThat(getResponse.getBody().data().coupon().minOrderAmount()).isEqualTo(20000L)
            );
        }

        @DisplayName("존재하지 않는 쿠폰을 수정하면, 404 Not Found 응답을 받는다.")
        @Test
        void returnsNotFound_whenCouponNotExists() {
            var request = new CouponV1Dto.UpdateCouponRequest("이름", 3000L, null, FUTURE, null);

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                COUPON_ADMIN + "/999", HttpMethod.PUT, adminEntity(request),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("DELETE /api-admin/v1/coupons/{couponId} (쿠폰 삭제)")
    @Nested
    class DeleteCoupon {

        @DisplayName("존재하는 쿠폰을 삭제하면, 200 OK 응답을 받고 조회 시 404가 반환된다.")
        @Test
        void returnsOk_whenCouponExists() {
            Long couponId = createCoupon("쿠폰", "FIXED", 5000L);

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                COUPON_ADMIN + "/" + couponId, HttpMethod.DELETE, adminEntity(null),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 삭제 후 조회 시 NOT_FOUND
            ResponseEntity<ApiResponse<CouponV1Dto.CouponResponse>> getResponse = testRestTemplate.exchange(
                COUPON_ADMIN + "/" + couponId, HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{couponId}/issues (발급 내역 조회)")
    @Nested
    class GetIssuedCoupons {

        @DisplayName("발급 내역이 있으면, 목록을 반환한다.")
        @Test
        void returnsIssuedCoupons_whenIssuesExist() {
            // Arrange
            Long couponId = createCoupon("쿠폰", "FIXED", 5000L);
            registerMember("user1", "Test1234!");
            registerMember("user2", "Test1234!");
            issueCoupon("user1", "Test1234!", couponId);
            issueCoupon("user2", "Test1234!", couponId);

            // Act
            ResponseEntity<ApiResponse<CouponV1Dto.MemberCouponListResponse>> response = testRestTemplate.exchange(
                COUPON_ADMIN + "/" + couponId + "/issues", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().memberCoupons()).hasSize(2)
            );
        }
    }

    // --- Helper Methods ---

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        headers.set("Content-Type", "application/json");
        return headers;
    }

    private <T> HttpEntity<T> adminEntity(T body) {
        return new HttpEntity<>(body, adminHeaders());
    }

    private Long createCoupon(String name, String type, Long value) {
        var request = new CouponV1Dto.CreateCouponRequest(name, type, value, null, FUTURE, null, null);
        ResponseEntity<ApiResponse<CouponV1Dto.CouponResponse>> response = testRestTemplate.exchange(
            COUPON_ADMIN, HttpMethod.POST, adminEntity(request),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().coupon().id();
    }

    private void registerMember(String loginId, String password) {
        var request = new MemberV1Dto.RegisterRequest(
            loginId, password, "홍길동", LocalDate.of(1990, 1, 15),
            "MALE", loginId + "@example.com", null
        );
        testRestTemplate.exchange(
            "/api/v1/members", HttpMethod.POST, new HttpEntity<>(request),
            new ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {}
        );
    }

    private void issueCoupon(String loginId, String password, Long couponId) {
        testRestTemplate.exchange(
            "/api/v1/coupons/" + couponId + "/issue", HttpMethod.POST,
            new HttpEntity<>(authHeaders(loginId, password)),
            new ParameterizedTypeReference<ApiResponse<CouponV1Dto.MemberCouponResponse>>() {}
        );
    }

    private HttpHeaders authHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, loginId);
        headers.set(HEADER_LOGIN_PW, password);
        return headers;
    }
}

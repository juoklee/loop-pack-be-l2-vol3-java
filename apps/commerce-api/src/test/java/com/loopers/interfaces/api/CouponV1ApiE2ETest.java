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
class CouponV1ApiE2ETest {

    private static final String COUPON_ADMIN = "/api-admin/v1/coupons";
    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    private static final LocalDateTime FUTURE = LocalDateTime.now().plusDays(30);
    private static final LocalDateTime PAST = LocalDateTime.now().minusDays(1);

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public CouponV1ApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/coupons/{couponId}/issue (쿠폰 발급)")
    @Nested
    class IssueCoupon {

        @DisplayName("유효한 쿠폰을 발급하면, 201 Created 응답을 받는다.")
        @Test
        void returnsCreated_whenCouponIsValid() {
            // Arrange
            Long couponId = createCoupon("5000원 할인", "FIXED", 5000L, FUTURE);
            registerMember("user1", "Test1234!");

            // Act
            ResponseEntity<ApiResponse<CouponV1Dto.MemberCouponResponse>> response = testRestTemplate.exchange(
                "/api/v1/coupons/" + couponId + "/issue", HttpMethod.POST,
                new HttpEntity<>(authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().data().memberCoupon().couponId()).isEqualTo(couponId),
                () -> assertThat(response.getBody().data().memberCoupon().status()).isEqualTo("AVAILABLE")
            );
        }

        @DisplayName("존재하지 않는 쿠폰을 발급하면, 404 Not Found 응답을 받는다.")
        @Test
        void returnsNotFound_whenCouponNotExists() {
            registerMember("user1", "Test1234!");

            ResponseEntity<ApiResponse<CouponV1Dto.MemberCouponResponse>> response = testRestTemplate.exchange(
                "/api/v1/coupons/999/issue", HttpMethod.POST,
                new HttpEntity<>(authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("만료된 쿠폰을 발급하면, 400 Bad Request 응답을 받는다.")
        @Test
        void returnsBadRequest_whenCouponIsExpired() {
            Long couponId = createCoupon("만료 쿠폰", "FIXED", 5000L, PAST);
            registerMember("user1", "Test1234!");

            ResponseEntity<ApiResponse<CouponV1Dto.MemberCouponResponse>> response = testRestTemplate.exchange(
                "/api/v1/coupons/" + couponId + "/issue", HttpMethod.POST,
                new HttpEntity<>(authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("수량이 소진된 쿠폰을 발급하면, 400 Bad Request 응답을 받는다.")
        @Test
        void returnsBadRequest_whenQuantityExhausted() {
            // Arrange — 수량 1개 쿠폰 생성
            Long couponId = createLimitedCoupon("선착순 쿠폰", "FIXED", 5000L, FUTURE, 1);
            registerMember("user1", "Test1234!");
            registerMember("user2", "Test1234!");

            // user1 발급 성공
            issueCoupon("user1", "Test1234!", couponId);

            // Act — user2 발급 시도
            ResponseEntity<ApiResponse<CouponV1Dto.MemberCouponResponse>> response = testRestTemplate.exchange(
                "/api/v1/coupons/" + couponId + "/issue", HttpMethod.POST,
                new HttpEntity<>(authHeaders("user2", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("validDays 쿠폰을 발급하면, 개인 만료일이 설정된다.")
        @Test
        void returnsCreated_withPersonalExpiredAt_whenValidDaysCoupon() {
            // Arrange — validDays=7인 기간제 쿠폰
            Long couponId = createValidDaysCoupon("기간제 쿠폰", "FIXED", 5000L, FUTURE, 7);
            registerMember("user1", "Test1234!");

            // Act
            ResponseEntity<ApiResponse<CouponV1Dto.MemberCouponResponse>> response = testRestTemplate.exchange(
                "/api/v1/coupons/" + couponId + "/issue", HttpMethod.POST,
                new HttpEntity<>(authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            // Assert — 개인 만료일이 쿠폰 만료일(30일 후)보다 이전(7일 후 근처)
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().data().memberCoupon().expiredAt()).isBefore(FUTURE),
                () -> assertThat(response.getBody().data().memberCoupon().expiredAt())
                    .isAfter(LocalDateTime.now().plusDays(6))
            );
        }

        @DisplayName("이미 발급받은 쿠폰을 다시 발급하면, 409 Conflict 응답을 받는다.")
        @Test
        void returnsConflict_whenAlreadyIssued() {
            Long couponId = createCoupon("쿠폰", "FIXED", 5000L, FUTURE);
            registerMember("user1", "Test1234!");

            // 첫 번째 발급
            testRestTemplate.exchange(
                "/api/v1/coupons/" + couponId + "/issue", HttpMethod.POST,
                new HttpEntity<>(authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<ApiResponse<CouponV1Dto.MemberCouponResponse>>() {}
            );

            // 두 번째 발급
            ResponseEntity<ApiResponse<CouponV1Dto.MemberCouponResponse>> response = testRestTemplate.exchange(
                "/api/v1/coupons/" + couponId + "/issue", HttpMethod.POST,
                new HttpEntity<>(authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @DisplayName("GET /api/v1/users/me/coupons (내 쿠폰 목록 조회)")
    @Nested
    class GetMyCoupons {

        @DisplayName("발급받은 쿠폰이 있으면, 목록을 반환한다.")
        @Test
        void returnsMyCoupons_whenCouponsExist() {
            // Arrange
            Long couponId1 = createCoupon("쿠폰1", "FIXED", 5000L, FUTURE);
            Long couponId2 = createCoupon("쿠폰2", "RATE", 10L, FUTURE);
            registerMember("user1", "Test1234!");
            issueCoupon("user1", "Test1234!", couponId1);
            issueCoupon("user1", "Test1234!", couponId2);

            // Act
            ResponseEntity<ApiResponse<CouponV1Dto.MemberCouponListResponse>> response = testRestTemplate.exchange(
                "/api/v1/users/me/coupons", HttpMethod.GET,
                new HttpEntity<>(authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().memberCoupons()).hasSize(2)
            );
        }

        @DisplayName("발급받은 쿠폰이 없으면, 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoCoupons() {
            registerMember("user1", "Test1234!");

            ResponseEntity<ApiResponse<CouponV1Dto.MemberCouponListResponse>> response = testRestTemplate.exchange(
                "/api/v1/users/me/coupons", HttpMethod.GET,
                new HttpEntity<>(authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().memberCoupons()).isEmpty()
            );
        }

        @DisplayName("여러 종류의 쿠폰을 발급받아도 한 번의 조회로 모두 반환된다.")
        @Test
        void returnsMyCoupons_whenManyCouponsIssued() {
            // Arrange — 5개 쿠폰 생성 후 모두 발급
            registerMember("user1", "Test1234!");
            for (int i = 1; i <= 5; i++) {
                Long couponId = createCoupon("쿠폰" + i, "FIXED", 1000L * i, FUTURE);
                issueCoupon("user1", "Test1234!", couponId);
            }

            // Act
            ResponseEntity<ApiResponse<CouponV1Dto.MemberCouponListResponse>> response = testRestTemplate.exchange(
                "/api/v1/users/me/coupons", HttpMethod.GET,
                new HttpEntity<>(authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().memberCoupons()).hasSize(5),
                () -> assertThat(response.getBody().data().memberCoupons())
                    .allSatisfy(mc -> assertThat(mc.coupon().name()).startsWith("쿠폰"))
            );
        }

        @DisplayName("다른 사용자의 쿠폰은 조회되지 않는다.")
        @Test
        void returnsOnlyMyCoupons_notOtherUsers() {
            Long couponId = createCoupon("쿠폰", "FIXED", 5000L, FUTURE);
            registerMember("user1", "Test1234!");
            registerMember("user2", "Test1234!");
            issueCoupon("user1", "Test1234!", couponId);

            ResponseEntity<ApiResponse<CouponV1Dto.MemberCouponListResponse>> response = testRestTemplate.exchange(
                "/api/v1/users/me/coupons", HttpMethod.GET,
                new HttpEntity<>(authHeaders("user2", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().memberCoupons()).isEmpty()
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

    private Long createLimitedCoupon(String name, String type, Long value, LocalDateTime expiredAt, Integer totalQuantity) {
        var request = new CouponV1Dto.CreateCouponRequest(name, type, value, null, expiredAt, null, totalQuantity);
        ResponseEntity<ApiResponse<CouponV1Dto.CouponResponse>> response = testRestTemplate.exchange(
            COUPON_ADMIN, HttpMethod.POST, adminEntity(request),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().coupon().id();
    }

    private Long createValidDaysCoupon(String name, String type, Long value, LocalDateTime expiredAt, Integer validDays) {
        var request = new CouponV1Dto.CreateCouponRequest(name, type, value, null, expiredAt, validDays, null);
        ResponseEntity<ApiResponse<CouponV1Dto.CouponResponse>> response = testRestTemplate.exchange(
            COUPON_ADMIN, HttpMethod.POST, adminEntity(request),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().coupon().id();
    }

    private Long createCoupon(String name, String type, Long value, LocalDateTime expiredAt) {
        var request = new CouponV1Dto.CreateCouponRequest(name, type, value, null, expiredAt, null, null);
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

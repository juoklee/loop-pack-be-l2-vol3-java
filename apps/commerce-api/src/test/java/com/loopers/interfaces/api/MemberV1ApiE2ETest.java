package com.loopers.interfaces.api;

import com.loopers.domain.member.Gender;
import com.loopers.infrastructure.member.MemberJpaRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemberV1ApiE2ETest {

    private static final String ENDPOINT_REGISTER = "/api/v1/members";
    private static final String ENDPOINT_ME = "/api/v1/members/me";
    private static final String ENDPOINT_CHANGE_PASSWORD = "/api/v1/members/me/password";
    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final TestRestTemplate testRestTemplate;
    private final MemberJpaRepository memberJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public MemberV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        MemberJpaRepository memberJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.memberJpaRepository = memberJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/members (회원가입)")
    @Nested
    class Register {

        @DisplayName("유효한 정보로 회원가입하면, 201 Created 응답을 받는다.")
        @Test
        void returnsCreated_whenValidRequest() {
            // arrange
            MemberV1Dto.RegisterRequest request = new MemberV1Dto.RegisterRequest(
                "testUser1",
                "Test1234!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                Gender.MALE,
                "test@example.com",
                "010-1234-5678"
            );

            // act
            ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                new HttpEntity<>(request),
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().data().member().loginId()).isEqualTo("testUser1"),
                () -> assertThat(response.getBody().data().member().gender()).isEqualTo(Gender.MALE),
                () -> assertThat(response.getBody().data().member().phone()).isEqualTo("010-1234-5678"),
                () -> assertThat(memberJpaRepository.existsByLoginId("testUser1")).isTrue()
            );
        }

        @DisplayName("이미 존재하는 로그인ID로 가입하면, 400 Bad Request 응답을 받는다.")
        @Test
        void returnsBadRequest_whenLoginIdAlreadyExists() {
            // arrange - 먼저 회원가입
            registerMember("existingUser", "Test1234!");

            // arrange - 같은 로그인ID로 다시 가입 시도
            MemberV1Dto.RegisterRequest duplicateRequest = new MemberV1Dto.RegisterRequest(
                "existingUser",
                "Test5678!",
                "김철수",
                LocalDate.of(1985, 5, 20),
                Gender.MALE,
                "second@example.com",
                null
            );

            // act
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                new HttpEntity<>(duplicateRequest),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("잘못된 이메일 형식으로 가입하면, 400 Bad Request 응답을 받는다.")
        @Test
        void returnsBadRequest_whenInvalidEmail() {
            // arrange
            MemberV1Dto.RegisterRequest request = new MemberV1Dto.RegisterRequest(
                "testUser2",
                "Test1234!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                Gender.MALE,
                "invalid-email",
                null
            );

            // act
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/members/me (내 정보 조회)")
    @Nested
    class GetMe {

        @DisplayName("유효한 인증 헤더로 조회하면, 200 OK와 마스킹된 이름을 반환한다.")
        @Test
        void returnsOk_whenValidAuth() {
            // arrange - 먼저 회원가입
            String loginId = "testUser1";
            String password = "Test1234!";
            registerMember(loginId, password);

            // arrange - 인증 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, loginId);
            headers.set(HEADER_LOGIN_PW, password);

            // act
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ME,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().member().loginId()).isEqualTo(loginId),
                () -> assertThat(response.getBody().data().member().name()).isEqualTo("홍길*"),
                () -> assertThat(response.getBody().data().member().gender()).isEqualTo(Gender.MALE),
                () -> assertThat(response.getBody().data().member().email()).isEqualTo(loginId + "@example.com")
            );
        }

        @DisplayName("인증 헤더가 없으면, 401 Unauthorized와 표준 에러 바디를 반환한다.")
        @Test
        void returnsUnauthorized_whenNoAuthHeader() {
            // act
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ME,
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("Unauthorized")
            );
        }

        @DisplayName("잘못된 비밀번호로 조회하면, 401 Unauthorized와 표준 에러 바디를 반환한다.")
        @Test
        void returnsUnauthorized_whenWrongPassword() {
            // arrange - 먼저 회원가입
            String loginId = "testUser2";
            registerMember(loginId, "Test1234!");

            // arrange - 잘못된 비밀번호로 인증 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, loginId);
            headers.set(HEADER_LOGIN_PW, "WrongPassword1!");

            // act
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ME,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("Unauthorized")
            );
        }
    }

    @DisplayName("PATCH /api/v1/members/me/password (비밀번호 수정)")
    @Nested
    class ChangePassword {

        @DisplayName("유효한 요청으로 비밀번호를 수정하면, 200 OK 응답을 받는다.")
        @Test
        void returnsOk_whenValidRequest() {
            // arrange - 먼저 회원가입
            String loginId = "testUser1";
            String currentPassword = "Test1234!";
            String newPassword = "NewPass5678!";
            registerMember(loginId, currentPassword);

            // arrange - 인증 헤더와 요청 본문 설정
            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, loginId);
            headers.set(HEADER_LOGIN_PW, currentPassword);

            MemberV1Dto.ChangePasswordRequest request = new MemberV1Dto.ChangePasswordRequest(
                currentPassword,
                newPassword
            );

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_CHANGE_PASSWORD,
                HttpMethod.PATCH,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert - 비밀번호 변경 성공
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // assert - 새 비밀번호로 인증 가능한지 확인
            HttpHeaders newAuthHeaders = new HttpHeaders();
            newAuthHeaders.set(HEADER_LOGIN_ID, loginId);
            newAuthHeaders.set(HEADER_LOGIN_PW, newPassword);

            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> meResponse = testRestTemplate.exchange(
                ENDPOINT_ME,
                HttpMethod.GET,
                new HttpEntity<>(newAuthHeaders),
                new ParameterizedTypeReference<>() {}
            );
            assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("현재 비밀번호가 일치하지 않으면, 400 Bad Request 응답을 받는다.")
        @Test
        void returnsBadRequest_whenCurrentPasswordNotMatch() {
            // arrange
            String loginId = "testUser2";
            String currentPassword = "Test1234!";
            registerMember(loginId, currentPassword);

            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, loginId);
            headers.set(HEADER_LOGIN_PW, currentPassword);

            MemberV1Dto.ChangePasswordRequest request = new MemberV1Dto.ChangePasswordRequest(
                "WrongCurrent1!",
                "NewPass5678!"
            );

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_CHANGE_PASSWORD,
                HttpMethod.PATCH,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 동일하면, 400 Bad Request 응답을 받는다.")
        @Test
        void returnsBadRequest_whenNewPasswordSameAsCurrent() {
            // arrange
            String loginId = "testUser3";
            String currentPassword = "Test1234!";
            registerMember(loginId, currentPassword);

            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, loginId);
            headers.set(HEADER_LOGIN_PW, currentPassword);

            MemberV1Dto.ChangePasswordRequest request = new MemberV1Dto.ChangePasswordRequest(
                currentPassword,
                currentPassword
            );

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_CHANGE_PASSWORD,
                HttpMethod.PATCH,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 규칙을 위반하면, 400 Bad Request 응답을 받는다.")
        @Test
        void returnsBadRequest_whenNewPasswordInvalid() {
            // arrange
            String loginId = "testUser4";
            String currentPassword = "Test1234!";
            registerMember(loginId, currentPassword);

            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, loginId);
            headers.set(HEADER_LOGIN_PW, currentPassword);

            MemberV1Dto.ChangePasswordRequest request = new MemberV1Dto.ChangePasswordRequest(
                currentPassword,
                "short"
            );

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_CHANGE_PASSWORD,
                HttpMethod.PATCH,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("인증 헤더가 없으면, 401 Unauthorized와 표준 에러 바디를 반환한다.")
        @Test
        void returnsUnauthorized_whenNoAuthHeader() {
            // arrange
            MemberV1Dto.ChangePasswordRequest request = new MemberV1Dto.ChangePasswordRequest(
                "Test1234!",
                "NewPass5678!"
            );

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_CHANGE_PASSWORD,
                HttpMethod.PATCH,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("Unauthorized")
            );
        }
    }

    @DisplayName("PATCH /api/v1/members/me (내 정보 수정)")
    @Nested
    class UpdatePhone {

        @DisplayName("유효한 전화번호로 수정하면, 200 OK 응답을 받는다.")
        @Test
        void returnsOk_whenValidPhone() {
            // arrange
            String loginId = "testUser1";
            String password = "Test1234!";
            registerMember(loginId, password);

            MemberV1Dto.UpdatePhoneRequest request = new MemberV1Dto.UpdatePhoneRequest("010-9999-8888");

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_ME,
                HttpMethod.PATCH,
                new HttpEntity<>(request, authHeaders(loginId, password)),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 수정 확인
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> meResponse = testRestTemplate.exchange(
                ENDPOINT_ME,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(loginId, password)),
                new ParameterizedTypeReference<>() {}
            );
            assertThat(meResponse.getBody().data().member().phone()).isEqualTo("010-9999-8888");
        }

        @DisplayName("잘못된 전화번호 형식이면, 400 Bad Request 응답을 받는다.")
        @Test
        void returnsBadRequest_whenInvalidPhone() {
            // arrange
            String loginId = "testUser2";
            String password = "Test1234!";
            registerMember(loginId, password);

            MemberV1Dto.UpdatePhoneRequest request = new MemberV1Dto.UpdatePhoneRequest("invalid");

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_ME,
                HttpMethod.PATCH,
                new HttpEntity<>(request, authHeaders(loginId, password)),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("DELETE /api/v1/members/me (회원 탈퇴)")
    @Nested
    class Withdraw {

        @DisplayName("비밀번호가 일치하면, 200 OK 응답을 받고 이후 로그인이 차단된다.")
        @Test
        void returnsOk_andBlocksLogin_whenPasswordMatches() {
            // arrange
            String loginId = "testUser1";
            String password = "Test1234!";
            registerMember(loginId, password);

            MemberV1Dto.WithdrawRequest request = new MemberV1Dto.WithdrawRequest(password);

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_ME,
                HttpMethod.DELETE,
                new HttpEntity<>(request, authHeaders(loginId, password)),
                new ParameterizedTypeReference<>() {}
            );

            // assert - 탈퇴 성공
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // assert - 탈퇴 후 로그인 시도 시 UNAUTHORIZED
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> meResponse = testRestTemplate.exchange(
                ENDPOINT_ME,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(loginId, password)),
                new ParameterizedTypeReference<>() {}
            );
            assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("비밀번호가 일치하지 않으면, 400 Bad Request 응답을 받는다.")
        @Test
        void returnsBadRequest_whenPasswordDoesNotMatch() {
            // arrange
            String loginId = "testUser2";
            String password = "Test1234!";
            registerMember(loginId, password);

            MemberV1Dto.WithdrawRequest request = new MemberV1Dto.WithdrawRequest("WrongPass1!");

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_ME,
                HttpMethod.DELETE,
                new HttpEntity<>(request, authHeaders(loginId, password)),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("탈퇴 후 동일 loginId로 재가입하면, 400 Bad Request 응답을 받는다.")
        @Test
        void returnsBadRequest_whenReRegisterWithSameLoginId() {
            // arrange - 가입 후 탈퇴
            String loginId = "testUser3";
            String password = "Test1234!";
            registerMember(loginId, password);

            testRestTemplate.exchange(
                ENDPOINT_ME,
                HttpMethod.DELETE,
                new HttpEntity<>(new MemberV1Dto.WithdrawRequest(password), authHeaders(loginId, password)),
                new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            // act - 동일 loginId로 재가입
            MemberV1Dto.RegisterRequest reRegisterRequest = new MemberV1Dto.RegisterRequest(
                loginId, "NewPass5678!", "김철수",
                LocalDate.of(1995, 3, 10), Gender.FEMALE, "new@example.com", null
            );
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                new HttpEntity<>(reRegisterRequest),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    private void registerMember(String loginId, String password) {
        MemberV1Dto.RegisterRequest registerRequest = new MemberV1Dto.RegisterRequest(
            loginId,
            password,
            "홍길동",
            LocalDate.of(1990, 1, 15),
            Gender.MALE,
            loginId + "@example.com",
            null
        );
        testRestTemplate.exchange(
            ENDPOINT_REGISTER,
            HttpMethod.POST,
            new HttpEntity<>(registerRequest),
            new ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {}
        );
    }

    private HttpHeaders authHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, loginId);
        headers.set(HEADER_LOGIN_PW, password);
        return headers;
    }
}

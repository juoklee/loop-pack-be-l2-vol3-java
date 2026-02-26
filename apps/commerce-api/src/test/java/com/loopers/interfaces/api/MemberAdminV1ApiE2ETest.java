package com.loopers.interfaces.api;

import com.loopers.interfaces.api.member.MemberAdminV1Dto;
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
class MemberAdminV1ApiE2ETest {

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public MemberAdminV1ApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("GET /api-admin/v1/members (회원 목록 조회)")
    @Nested
    class GetMembers {

        @DisplayName("전체 회원 목록을 조회한다.")
        @Test
        void returnsAllMembers() {
            // Arrange
            registerMember("user1", "홍길동", "user1@example.com");
            registerMember("user2", "김영희", "user2@example.com");
            registerMember("user3", "이철수", "user3@example.com");

            // Act
            ResponseEntity<ApiResponse<MemberAdminV1Dto.MemberListResponse>> response = testRestTemplate.exchange(
                "/api-admin/v1/members?page=0&size=20",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().members()).hasSize(3),
                () -> assertThat(response.getBody().data().page().totalElements()).isEqualTo(3)
            );
        }

        @DisplayName("keyword로 loginId를 검색한다.")
        @Test
        void searchesByLoginId() {
            // Arrange
            registerMember("admin01", "홍길동", "admin01@example.com");
            registerMember("user01", "김영희", "user01@example.com");
            registerMember("user02", "이철수", "user02@example.com");

            // Act
            ResponseEntity<ApiResponse<MemberAdminV1Dto.MemberListResponse>> response = testRestTemplate.exchange(
                "/api-admin/v1/members?keyword=admin&page=0&size=20",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().members()).hasSize(1),
                () -> assertThat(response.getBody().data().members().get(0).loginId()).isEqualTo("admin01")
            );
        }

        @DisplayName("keyword로 이름을 검색한다.")
        @Test
        void searchesByName() {
            // Arrange
            registerMember("user1", "홍길동", "user1@example.com");
            registerMember("user2", "김영희", "user2@example.com");

            // Act
            ResponseEntity<ApiResponse<MemberAdminV1Dto.MemberListResponse>> response = testRestTemplate.exchange(
                "/api-admin/v1/members?keyword=홍길동&page=0&size=20",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().members()).hasSize(1),
                () -> assertThat(response.getBody().data().members().get(0).name()).isEqualTo("홍길동")
            );
        }

        @DisplayName("keyword로 이메일을 검색한다.")
        @Test
        void searchesByEmail() {
            // Arrange
            registerMember("user1", "홍길동", "hong@loopers.com");
            registerMember("user2", "김영희", "kim@example.com");

            // Act
            ResponseEntity<ApiResponse<MemberAdminV1Dto.MemberListResponse>> response = testRestTemplate.exchange(
                "/api-admin/v1/members?keyword=loopers&page=0&size=20",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().members()).hasSize(1),
                () -> assertThat(response.getBody().data().members().get(0).loginId()).isEqualTo("user1")
            );
        }

        @DisplayName("keyword에 매칭되는 회원이 없으면 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoMatch() {
            // Arrange
            registerMember("user1", "홍길동", "user1@example.com");

            // Act
            ResponseEntity<ApiResponse<MemberAdminV1Dto.MemberListResponse>> response = testRestTemplate.exchange(
                "/api-admin/v1/members?keyword=nonexistent&page=0&size=20",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().members()).isEmpty(),
                () -> assertThat(response.getBody().data().page().totalElements()).isEqualTo(0)
            );
        }

        @DisplayName("페이징이 정상 동작한다.")
        @Test
        void returnsPaginatedResults() {
            // Arrange
            registerMember("user1", "홍길동", "user1@example.com");
            registerMember("user2", "김영희", "user2@example.com");
            registerMember("user3", "이철수", "user3@example.com");

            // Act
            ResponseEntity<ApiResponse<MemberAdminV1Dto.MemberListResponse>> response = testRestTemplate.exchange(
                "/api-admin/v1/members?page=0&size=2",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().members()).hasSize(2),
                () -> assertThat(response.getBody().data().page().totalElements()).isEqualTo(3),
                () -> assertThat(response.getBody().data().page().totalPages()).isEqualTo(2)
            );
        }

        @DisplayName("Admin 인증 없이 요청하면, 401 Unauthorized를 반환한다.")
        @Test
        void returnsUnauthorized_withoutAdminAuth() {
            // Act
            ResponseEntity<ApiResponse<MemberAdminV1Dto.MemberListResponse>> response = testRestTemplate.exchange(
                "/api-admin/v1/members?page=0&size=20",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api-admin/v1/members/{memberId} (회원 상세 조회)")
    @Nested
    class GetMember {

        @DisplayName("회원 상세 정보를 조회한다 (마스킹 없음).")
        @Test
        void returnsMemberDetail_withoutMasking() {
            // Arrange
            registerMember("user1", "홍길동", "user1@example.com");
            Long memberId = getMemberIdByKeyword("user1");

            // Act
            ResponseEntity<ApiResponse<MemberAdminV1Dto.MemberResponse>> response = testRestTemplate.exchange(
                "/api-admin/v1/members/" + memberId,
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().member().id()).isEqualTo(memberId),
                () -> assertThat(response.getBody().data().member().loginId()).isEqualTo("user1"),
                () -> assertThat(response.getBody().data().member().name()).isEqualTo("홍길동"),
                () -> assertThat(response.getBody().data().member().email()).isEqualTo("user1@example.com"),
                () -> assertThat(response.getBody().data().member().gender()).isEqualTo("MALE"),
                () -> assertThat(response.getBody().data().member().createdAt()).isNotNull()
            );
        }

        @DisplayName("존재하지 않는 회원을 조회하면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenMemberNotExists() {
            // Act
            ResponseEntity<ApiResponse<MemberAdminV1Dto.MemberResponse>> response = testRestTemplate.exchange(
                "/api-admin/v1/members/999999",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("탈퇴한 회원을 조회하면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenMemberWithdrawn() {
            // Arrange
            registerMember("user1", "홍길동", "user1@example.com");
            Long memberId = getMemberIdByKeyword("user1");

            // 회원 탈퇴
            var withdrawRequest = new MemberV1Dto.WithdrawRequest("Test1234!");
            HttpHeaders authHeaders = new HttpHeaders();
            authHeaders.set("X-Loopers-LoginId", "user1");
            authHeaders.set("X-Loopers-LoginPw", "Test1234!");
            testRestTemplate.exchange(
                "/api/v1/members/me",
                HttpMethod.DELETE,
                new HttpEntity<>(withdrawRequest, authHeaders),
                new ParameterizedTypeReference<ApiResponse<Object>>() {}
            );

            // Act
            ResponseEntity<ApiResponse<MemberAdminV1Dto.MemberResponse>> response = testRestTemplate.exchange(
                "/api-admin/v1/members/" + memberId,
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // --- Helper Methods ---

    private void registerMember(String loginId, String name, String email) {
        var request = new MemberV1Dto.RegisterRequest(
            loginId, "Test1234!", name, LocalDate.of(1990, 1, 15),
            "MALE", email, "010-1234-5678"
        );
        testRestTemplate.exchange(
            "/api/v1/members",
            HttpMethod.POST,
            new HttpEntity<>(request),
            new ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {}
        );
    }

    private Long getMemberIdByKeyword(String keyword) {
        ResponseEntity<ApiResponse<MemberAdminV1Dto.MemberListResponse>> response = testRestTemplate.exchange(
            "/api-admin/v1/members?keyword=" + keyword + "&page=0&size=1",
            HttpMethod.GET,
            new HttpEntity<>(adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().members().get(0).id();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        headers.set("Content-Type", "application/json");
        return headers;
    }
}

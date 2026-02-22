package com.loopers.support.auth;

import com.loopers.interfaces.api.ApiResponse;
import org.junit.jupiter.api.DisplayName;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminAuthFilterE2ETest {

    private static final String ADMIN_ENDPOINT = "/api-admin/v1/brands";

    private final TestRestTemplate testRestTemplate;

    @Autowired
    public AdminAuthFilterE2ETest(TestRestTemplate testRestTemplate) {
        this.testRestTemplate = testRestTemplate;
    }

    @DisplayName("X-Loopers-Ldap 헤더 없이 Admin API에 접근하면, 401 응답을 받는다.")
    @Test
    void returnsUnauthorized_whenNoLdapHeader() {
        ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
            ADMIN_ENDPOINT,
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @DisplayName("잘못된 X-Loopers-Ldap 헤더로 Admin API에 접근하면, 401 응답을 받는다.")
    @Test
    void returnsUnauthorized_whenInvalidLdapHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "wrong.value");

        ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
            ADMIN_ENDPOINT,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @DisplayName("올바른 X-Loopers-Ldap 헤더로 Admin API에 접근하면, 인증을 통과한다.")
    @Test
    void passesAuthentication_whenValidLdapHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");

        ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
            ADMIN_ENDPOINT,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            new ParameterizedTypeReference<>() {}
        );

        // 인증 통과 후 실제 API가 동작하므로 401이 아닌 다른 응답
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @DisplayName("Public API는 X-Loopers-Ldap 헤더 없이도 접근 가능하다.")
    @Test
    void allowsPublicApi_withoutLdapHeader() {
        ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
            "/api/v1/brands",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }
}

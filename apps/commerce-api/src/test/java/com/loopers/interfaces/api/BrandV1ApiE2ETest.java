package com.loopers.interfaces.api;

import com.loopers.interfaces.api.brand.BrandV1Dto;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandV1ApiE2ETest {

    private static final String ENDPOINT_PUBLIC = "/api/v1/brands";
    private static final String ENDPOINT_ADMIN = "/api-admin/v1/brands";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public BrandV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api-admin/v1/brands (브랜드 등록)")
    @Nested
    class Register {

        @DisplayName("유효한 정보로 등록하면, 201 Created 응답을 받는다.")
        @Test
        void returnsCreated_whenValidRequest() {
            // Arrange
            BrandV1Dto.RegisterRequest request = new BrandV1Dto.RegisterRequest("Nike", "Just Do It");

            // Act
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ADMIN,
                HttpMethod.POST,
                adminEntity(request),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().data().brand().name()).isEqualTo("Nike"),
                () -> assertThat(response.getBody().data().brand().description()).isEqualTo("Just Do It"),
                () -> assertThat(response.getBody().data().brand().likeCount()).isZero()
            );
        }

        @DisplayName("이름이 빈 문자열이면, 400 Bad Request 응답을 받는다.")
        @Test
        void returnsBadRequest_whenNameIsBlank() {
            // Arrange
            BrandV1Dto.RegisterRequest request = new BrandV1Dto.RegisterRequest("  ", "설명");

            // Act
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ADMIN,
                HttpMethod.POST,
                adminEntity(request),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("동일 브랜드명으로 등록하면, 400 Bad Request 응답을 받는다.")
        @Test
        void returnsBadRequest_whenNameAlreadyExists() {
            // Arrange
            registerBrand("Nike", "Just Do It");

            // Act
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ADMIN,
                HttpMethod.POST,
                adminEntity(new BrandV1Dto.RegisterRequest("Nike", "다른 설명")),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/brands (브랜드 목록 조회)")
    @Nested
    class GetBrands {

        @DisplayName("브랜드를 등록한 후 목록 조회하면, 등록된 브랜드가 포함된다.")
        @Test
        void returnsBrands_whenBrandsExist() {
            // Arrange
            registerBrand("Nike", "Just Do It");
            registerBrand("Adidas", "Impossible Is Nothing");

            // Act
            ResponseEntity<ApiResponse<BrandV1Dto.BrandListResponse>> response = testRestTemplate.exchange(
                ENDPOINT_PUBLIC,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().brands()).hasSize(2)
            );
        }

        @DisplayName("키워드로 검색하면, 일치하는 브랜드만 반환한다.")
        @Test
        void returnsFilteredBrands_whenKeywordProvided() {
            // Arrange
            registerBrand("Nike", "Just Do It");
            registerBrand("Adidas", "Impossible Is Nothing");

            // Act
            ResponseEntity<ApiResponse<BrandV1Dto.BrandListResponse>> response = testRestTemplate.exchange(
                ENDPOINT_PUBLIC + "?keyword=Nik",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().brands()).hasSize(1),
                () -> assertThat(response.getBody().data().brands().get(0).name()).isEqualTo("Nike")
            );
        }
    }

    @DisplayName("GET /api/v1/brands/{brandId} (브랜드 상세 조회)")
    @Nested
    class GetBrand {

        @DisplayName("존재하는 브랜드를 조회하면, 200 OK 응답을 받는다.")
        @Test
        void returnsOk_whenBrandExists() {
            // Arrange
            Long brandId = registerBrand("Nike", "Just Do It");

            // Act
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT_PUBLIC + "/" + brandId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().brand().name()).isEqualTo("Nike")
            );
        }

        @DisplayName("존재하지 않는 브랜드를 조회하면, 404 Not Found 응답을 받는다.")
        @Test
        void returnsNotFound_whenBrandNotExists() {
            // Act
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT_PUBLIC + "/999",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("PUT /api-admin/v1/brands/{brandId} (브랜드 수정)")
    @Nested
    class Update {

        @DisplayName("유효한 정보로 수정하면, 200 OK 응답을 받는다.")
        @Test
        void returnsOk_whenValidRequest() {
            // Arrange
            Long brandId = registerBrand("Nike", "Just Do It");

            // Act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_ADMIN + "/" + brandId,
                HttpMethod.PUT,
                adminEntity(new BrandV1Dto.UpdateRequest("Adidas", "Impossible Is Nothing")),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 수정 확인
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> getResponse = testRestTemplate.exchange(
                ENDPOINT_PUBLIC + "/" + brandId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );
            assertThat(getResponse.getBody().data().brand().name()).isEqualTo("Adidas");
        }

        @DisplayName("존재하지 않는 브랜드를 수정하면, 404 Not Found 응답을 받는다.")
        @Test
        void returnsNotFound_whenBrandNotExists() {
            // Act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_ADMIN + "/999",
                HttpMethod.PUT,
                adminEntity(new BrandV1Dto.UpdateRequest("Adidas", "설명")),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("DELETE /api-admin/v1/brands/{brandId} (브랜드 삭제)")
    @Nested
    class Delete {

        @DisplayName("존재하는 브랜드를 삭제하면, 200 OK 응답을 받고 조회 시 404가 반환된다.")
        @Test
        void returnsOk_whenBrandExists() {
            // Arrange
            Long brandId = registerBrand("Nike", "Just Do It");

            // Act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_ADMIN + "/" + brandId,
                HttpMethod.DELETE,
                adminEntity(null),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 삭제 후 조회 시 NOT_FOUND
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> getResponse = testRestTemplate.exchange(
                ENDPOINT_PUBLIC + "/" + brandId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 브랜드를 삭제하면, 404 Not Found 응답을 받는다.")
        @Test
        void returnsNotFound_whenBrandNotExists() {
            // Act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_ADMIN + "/999",
                HttpMethod.DELETE,
                adminEntity(null),
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
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

    private Long registerBrand(String name, String description) {
        BrandV1Dto.RegisterRequest request = new BrandV1Dto.RegisterRequest(name, description);
        ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
            ENDPOINT_ADMIN,
            HttpMethod.POST,
            adminEntity(request),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().brand().id();
    }
}

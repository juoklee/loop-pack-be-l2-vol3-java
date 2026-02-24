package com.loopers.interfaces.api;

import com.loopers.domain.member.Gender;
import com.loopers.interfaces.api.address.AddressV1Dto;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AddressV1ApiE2ETest {

    private static final String ENDPOINT_MEMBERS = "/api/v1/members";
    private static final String ENDPOINT_ADDRESSES = "/api/v1/members/me/addresses";
    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public AddressV1ApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/members/me/addresses (배송지 등록)")
    @Nested
    class Register {

        @DisplayName("첫 배송지 등록 시, 201 Created와 isDefault=true를 반환한다.")
        @Test
        void returnsCreated_withDefaultTrue_whenFirstAddress() {
            // arrange
            registerMember("user1", "Test1234!");
            AddressV1Dto.CreateAddressRequest request = createAddressRequest("집", "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", "101호");

            // act
            ResponseEntity<ApiResponse<AddressV1Dto.AddressResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ADDRESSES,
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().data().address().label()).isEqualTo("집"),
                () -> assertThat(response.getBody().data().address().recipientName()).isEqualTo("홍길동"),
                () -> assertThat(response.getBody().data().address().isDefault()).isTrue()
            );
        }

        @DisplayName("두 번째 배송지 등록 시, isDefault=false를 반환한다.")
        @Test
        void returnsCreated_withDefaultFalse_whenSecondAddress() {
            // arrange
            registerMember("user1", "Test1234!");
            HttpHeaders headers = authHeaders("user1", "Test1234!");
            registerAddress(headers, "집", "홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호");

            AddressV1Dto.CreateAddressRequest request = createAddressRequest("회사", "홍길동", "010-1234-5678",
                "54321", "서울시 서초구", "202호");

            // act
            ResponseEntity<ApiResponse<AddressV1Dto.AddressResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ADDRESSES,
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().data().address().label()).isEqualTo("회사"),
                () -> assertThat(response.getBody().data().address().isDefault()).isFalse()
            );
        }

        @DisplayName("최대 10개 초과 등록 시, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenExceedsMaxCount() {
            // arrange
            registerMember("user1", "Test1234!");
            HttpHeaders headers = authHeaders("user1", "Test1234!");
            for (int i = 0; i < 10; i++) {
                registerAddress(headers, "배송지" + i, "홍길동", "010-1234-5678",
                    "1234" + i, "주소" + i, null);
            }

            AddressV1Dto.CreateAddressRequest request = createAddressRequest("11번째", "홍길동", "010-1234-5678",
                "99999", "초과 주소", null);

            // act
            ResponseEntity<ApiResponse<AddressV1Dto.AddressResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ADDRESSES,
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("필수 필드가 누락되면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenRequiredFieldMissing() {
            // arrange
            registerMember("user1", "Test1234!");
            AddressV1Dto.CreateAddressRequest request = createAddressRequest(null, "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", null);

            // act
            ResponseEntity<ApiResponse<AddressV1Dto.AddressResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ADDRESSES,
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("인증 없이 접근하면, 401 Unauthorized를 반환한다.")
        @Test
        void returnsUnauthorized_whenNoAuth() {
            // arrange
            AddressV1Dto.CreateAddressRequest request = createAddressRequest("집", "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", null);

            // act
            ResponseEntity<ApiResponse<AddressV1Dto.AddressResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ADDRESSES,
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api/v1/members/me/addresses (배송지 목록 조회)")
    @Nested
    class GetAddresses {

        @DisplayName("본인의 배송지만 반환한다.")
        @Test
        void returnsOnlyOwnAddresses() {
            // arrange - 두 명의 회원 각각 배송지 등록
            registerMember("user1", "Test1234!");
            registerMember("user2", "Test1234!");
            HttpHeaders headers1 = authHeaders("user1", "Test1234!");
            HttpHeaders headers2 = authHeaders("user2", "Test1234!");

            registerAddress(headers1, "user1 집", "홍길동", "010-1111-1111", "11111", "주소1", null);
            registerAddress(headers1, "user1 회사", "홍길동", "010-1111-1111", "11112", "주소2", null);
            registerAddress(headers2, "user2 집", "김철수", "010-2222-2222", "22222", "주소3", null);

            // act
            ResponseEntity<ApiResponse<AddressV1Dto.AddressListResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ADDRESSES,
                HttpMethod.GET,
                new HttpEntity<>(headers1),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().addresses()).hasSize(2),
                () -> assertThat(response.getBody().data().addresses())
                    .extracting(AddressV1Dto.AddressDto::label)
                    .containsExactlyInAnyOrder("user1 집", "user1 회사")
            );
        }
    }

    @DisplayName("PUT /api/v1/members/me/addresses/{addressId} (배송지 수정)")
    @Nested
    class Update {

        @DisplayName("유효한 요청으로 수정하면, 200 OK를 반환한다.")
        @Test
        void returnsOk_whenValidRequest() {
            // arrange
            registerMember("user1", "Test1234!");
            HttpHeaders headers = authHeaders("user1", "Test1234!");
            Long addressId = registerAddressAndGetId(headers, "집", "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", "101호");

            AddressV1Dto.UpdateAddressRequest request = new AddressV1Dto.UpdateAddressRequest(
                "새집", "홍길동", "010-9999-9999", "54321", "서울시 서초구", "202호"
            );

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_ADDRESSES + "/" + addressId,
                HttpMethod.PUT,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 수정 확인
            ResponseEntity<ApiResponse<AddressV1Dto.AddressListResponse>> listResponse = testRestTemplate.exchange(
                ENDPOINT_ADDRESSES,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );
            assertThat(listResponse.getBody().data().addresses().get(0).label()).isEqualTo("새집");
        }

        @DisplayName("존재하지 않는 배송지를 수정하면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenAddressNotExists() {
            // arrange
            registerMember("user1", "Test1234!");
            AddressV1Dto.UpdateAddressRequest request = new AddressV1Dto.UpdateAddressRequest(
                "새집", "홍길동", "010-9999-9999", "54321", "서울시 서초구", null
            );

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_ADDRESSES + "/9999",
                HttpMethod.PUT,
                new HttpEntity<>(request, authHeaders("user1", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("타인의 배송지를 수정하면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenOtherMemberAddress() {
            // arrange
            registerMember("user1", "Test1234!");
            registerMember("user2", "Test1234!");
            Long addressId = registerAddressAndGetId(authHeaders("user1", "Test1234!"),
                "집", "홍길동", "010-1234-5678", "12345", "서울시 강남구", null);

            AddressV1Dto.UpdateAddressRequest request = new AddressV1Dto.UpdateAddressRequest(
                "탈취", "해커", "010-0000-0000", "00000", "해킹 주소", null
            );

            // act - user2가 user1의 배송지 수정 시도
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_ADDRESSES + "/" + addressId,
                HttpMethod.PUT,
                new HttpEntity<>(request, authHeaders("user2", "Test1234!")),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("DELETE /api/v1/members/me/addresses/{addressId} (배송지 삭제)")
    @Nested
    class Delete {

        @DisplayName("비기본 배송지를 삭제하면, 200 OK를 반환한다.")
        @Test
        void returnsOk_whenNonDefaultAddress() {
            // arrange
            registerMember("user1", "Test1234!");
            HttpHeaders headers = authHeaders("user1", "Test1234!");
            registerAddress(headers, "집", "홍길동", "010-1234-5678", "12345", "서울시 강남구", null);
            Long secondId = registerAddressAndGetId(headers, "회사", "홍길동", "010-1234-5678",
                "54321", "서울시 서초구", null);

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_ADDRESSES + "/" + secondId,
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 삭제 확인
            ResponseEntity<ApiResponse<AddressV1Dto.AddressListResponse>> listResponse = testRestTemplate.exchange(
                ENDPOINT_ADDRESSES,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );
            assertThat(listResponse.getBody().data().addresses()).hasSize(1);
        }

        @DisplayName("기본 배송지를 삭제하면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenDefaultAddress() {
            // arrange
            registerMember("user1", "Test1234!");
            HttpHeaders headers = authHeaders("user1", "Test1234!");
            Long defaultId = registerAddressAndGetId(headers, "집", "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", null);

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_ADDRESSES + "/" + defaultId,
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("PATCH /api/v1/members/me/addresses/{addressId}/default (기본 배송지 설정)")
    @Nested
    class SetDefault {

        @DisplayName("기본 배송지를 변경하면, 200 OK를 반환하고 기존 기본이 해제된다.")
        @Test
        void returnsOk_andChangesDefault() {
            // arrange
            registerMember("user1", "Test1234!");
            HttpHeaders headers = authHeaders("user1", "Test1234!");
            Long firstId = registerAddressAndGetId(headers, "집", "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", null);
            Long secondId = registerAddressAndGetId(headers, "회사", "홍길동", "010-1234-5678",
                "54321", "서울시 서초구", null);

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_ADDRESSES + "/" + secondId + "/default",
                HttpMethod.PATCH,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 기본 배송지 변경 확인
            ResponseEntity<ApiResponse<AddressV1Dto.AddressListResponse>> listResponse = testRestTemplate.exchange(
                ENDPOINT_ADDRESSES,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );
            List<AddressV1Dto.AddressDto> addresses = listResponse.getBody().data().addresses();
            AddressV1Dto.AddressDto first = addresses.stream().filter(a -> a.id().equals(firstId)).findFirst().get();
            AddressV1Dto.AddressDto second = addresses.stream().filter(a -> a.id().equals(secondId)).findFirst().get();

            assertAll(
                () -> assertThat(first.isDefault()).isFalse(),
                () -> assertThat(second.isDefault()).isTrue()
            );
        }
    }

    // --- Helper Methods ---

    private void registerMember(String loginId, String password) {
        MemberV1Dto.RegisterRequest request = new MemberV1Dto.RegisterRequest(
            loginId, password, "홍길동", LocalDate.of(1990, 1, 15),
            Gender.MALE, loginId + "@example.com", null
        );
        testRestTemplate.exchange(
            ENDPOINT_MEMBERS,
            HttpMethod.POST,
            new HttpEntity<>(request),
            new ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {}
        );
    }

    private void registerAddress(HttpHeaders headers, String label, String recipientName,
                                 String recipientPhone, String zipCode, String address1, String address2) {
        AddressV1Dto.CreateAddressRequest request = createAddressRequest(
            label, recipientName, recipientPhone, zipCode, address1, address2);
        testRestTemplate.exchange(
            ENDPOINT_ADDRESSES,
            HttpMethod.POST,
            new HttpEntity<>(request, headers),
            new ParameterizedTypeReference<ApiResponse<AddressV1Dto.AddressResponse>>() {}
        );
    }

    private Long registerAddressAndGetId(HttpHeaders headers, String label, String recipientName,
                                         String recipientPhone, String zipCode, String address1, String address2) {
        AddressV1Dto.CreateAddressRequest request = createAddressRequest(
            label, recipientName, recipientPhone, zipCode, address1, address2);
        ResponseEntity<ApiResponse<AddressV1Dto.AddressResponse>> response = testRestTemplate.exchange(
            ENDPOINT_ADDRESSES,
            HttpMethod.POST,
            new HttpEntity<>(request, headers),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().address().id();
    }

    private AddressV1Dto.CreateAddressRequest createAddressRequest(String label, String recipientName,
                                                                    String recipientPhone, String zipCode,
                                                                    String address1, String address2) {
        return new AddressV1Dto.CreateAddressRequest(label, recipientName, recipientPhone, zipCode, address1, address2);
    }

    private HttpHeaders authHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, loginId);
        headers.set(HEADER_LOGIN_PW, password);
        return headers;
    }
}

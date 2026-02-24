package com.loopers.domain.address;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AddressTest {

    @DisplayName("배송지를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("모든 필드가 유효하면, 정상적으로 생성된다.")
        @Test
        void createsAddress_whenAllFieldsAreValid() {
            // Arrange & Act
            Address address = Address.create(1L, "집", "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", "101동 202호", true);

            // Assert
            assertAll(
                () -> assertThat(address.getMemberId()).isEqualTo(1L),
                () -> assertThat(address.getLabel()).isEqualTo("집"),
                () -> assertThat(address.getRecipientName()).isEqualTo("홍길동"),
                () -> assertThat(address.getRecipientPhone()).isEqualTo("010-1234-5678"),
                () -> assertThat(address.getZipCode()).isEqualTo("12345"),
                () -> assertThat(address.getAddress1()).isEqualTo("서울시 강남구"),
                () -> assertThat(address.getAddress2()).isEqualTo("101동 202호"),
                () -> assertThat(address.getIsDefault()).isTrue()
            );
        }

        @DisplayName("상세주소가 null이어도, 정상적으로 생성된다.")
        @Test
        void createsAddress_whenAddress2IsNull() {
            // Arrange & Act
            Address address = Address.create(1L, "회사", "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", null, false);

            // Assert
            assertThat(address.getAddress2()).isNull();
        }

        @DisplayName("배송지명이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLabelIsBlank() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                Address.create(1L, "  ", "홍길동", "010-1234-5678",
                    "12345", "서울시 강남구", null, false)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수령인 이름이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenRecipientNameIsNull() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                Address.create(1L, "집", null, "010-1234-5678",
                    "12345", "서울시 강남구", null, false)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수령인 전화번호가 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenRecipientPhoneIsBlank() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                Address.create(1L, "집", "홍길동", "  ",
                    "12345", "서울시 강남구", null, false)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("우편번호가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenZipCodeIsNull() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                Address.create(1L, "집", "홍길동", "010-1234-5678",
                    null, "서울시 강남구", null, false)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("기본주소가 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAddress1IsBlank() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                Address.create(1L, "집", "홍길동", "010-1234-5678",
                    "12345", "  ", null, false)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("배송지를 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("유효한 정보로 수정하면, 정상적으로 수정된다.")
        @Test
        void updatesAddress_whenFieldsAreValid() {
            // Arrange
            Address address = Address.create(1L, "집", "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", "101동 202호", true);

            // Act
            address.update("회사", "김철수", "010-9876-5432",
                "54321", "서울시 서초구", "301동 402호");

            // Assert
            assertAll(
                () -> assertThat(address.getLabel()).isEqualTo("회사"),
                () -> assertThat(address.getRecipientName()).isEqualTo("김철수"),
                () -> assertThat(address.getRecipientPhone()).isEqualTo("010-9876-5432"),
                () -> assertThat(address.getZipCode()).isEqualTo("54321"),
                () -> assertThat(address.getAddress1()).isEqualTo("서울시 서초구"),
                () -> assertThat(address.getAddress2()).isEqualTo("301동 402호"),
                () -> assertThat(address.getIsDefault()).isTrue() // isDefault는 변경되지 않음
            );
        }

        @DisplayName("배송지명이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLabelIsNull() {
            // Arrange
            Address address = Address.create(1L, "집", "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", null, false);

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                address.update(null, "홍길동", "010-1234-5678",
                    "12345", "서울시 강남구", null)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("기본주소가 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAddress1IsBlank() {
            // Arrange
            Address address = Address.create(1L, "집", "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", null, false);

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                address.update("집", "홍길동", "010-1234-5678",
                    "12345", "  ", null)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("기본 배송지를 설정할 때, ")
    @Nested
    class SetDefault {

        @DisplayName("true로 설정하면, 기본 배송지가 된다.")
        @Test
        void setsDefault_whenTrue() {
            // Arrange
            Address address = Address.create(1L, "집", "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", null, false);

            // Act
            address.setDefault(true);

            // Assert
            assertThat(address.getIsDefault()).isTrue();
        }

        @DisplayName("false로 설정하면, 기본 배송지가 해제된다.")
        @Test
        void unsetsDefault_whenFalse() {
            // Arrange
            Address address = Address.create(1L, "집", "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", null, true);

            // Act
            address.setDefault(false);

            // Assert
            assertThat(address.getIsDefault()).isFalse();
        }
    }
}

package com.loopers.domain.member;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemberTest {

    private final PasswordEncoder stubEncoder = new PasswordEncoder() {
        @Override
        public String encode(String rawPassword) {
            return "encoded_" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String encodedPassword) {
            return encodedPassword.equals("encoded_" + rawPassword);
        }
    };

    private Member createDefaultMember() {
        return Member.create(
            "testuser1", "Test1234!", "홍길동",
            LocalDate.of(1990, 1, 15), Gender.MALE, "test@example.com", null, stubEncoder
        );
    }

    @DisplayName("필수값 검증 시, ")
    @Nested
    class ValidateRequired {

        @DisplayName("로그인ID가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdIsNull() {
            CoreException exception = assertThrows(CoreException.class, () -> {
                Member.create(null, "Test1234!", "홍길동",
                    LocalDate.of(1990, 1, 15), Gender.MALE, "test@example.com", null, stubEncoder);
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(exception.getMessage()).isEqualTo("로그인ID는 필수입니다.");
        }

        @DisplayName("로그인ID가 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdIsBlank() {
            CoreException exception = assertThrows(CoreException.class, () -> {
                Member.create("  ", "Test1234!", "홍길동",
                    LocalDate.of(1990, 1, 15), Gender.MALE, "test@example.com", null, stubEncoder);
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(exception.getMessage()).isEqualTo("로그인ID는 필수입니다.");
        }

        @DisplayName("비밀번호가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsNull() {
            CoreException exception = assertThrows(CoreException.class, () -> {
                Member.create("testuser1", null, "홍길동",
                    LocalDate.of(1990, 1, 15), Gender.MALE, "test@example.com", null, stubEncoder);
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(exception.getMessage()).isEqualTo("비밀번호는 필수입니다.");
        }

        @DisplayName("이름이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            CoreException exception = assertThrows(CoreException.class, () -> {
                Member.create("testuser1", "Test1234!", null,
                    LocalDate.of(1990, 1, 15), Gender.MALE, "test@example.com", null, stubEncoder);
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(exception.getMessage()).isEqualTo("이름은 필수입니다.");
        }

        @DisplayName("생년월일이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBirthDateIsNull() {
            CoreException exception = assertThrows(CoreException.class, () -> {
                Member.create("testuser1", "Test1234!", "홍길동",
                    null, Gender.MALE, "test@example.com", null, stubEncoder);
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(exception.getMessage()).isEqualTo("생년월일은 필수입니다.");
        }

        @DisplayName("성별이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenGenderIsNull() {
            CoreException exception = assertThrows(CoreException.class, () -> {
                Member.create("testuser1", "Test1234!", "홍길동",
                    LocalDate.of(1990, 1, 15), null, "test@example.com", null, stubEncoder);
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(exception.getMessage()).isEqualTo("성별은 필수입니다.");
        }

        @DisplayName("이메일이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailIsNull() {
            CoreException exception = assertThrows(CoreException.class, () -> {
                Member.create("testuser1", "Test1234!", "홍길동",
                    LocalDate.of(1990, 1, 15), Gender.MALE, null, null, stubEncoder);
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(exception.getMessage()).isEqualTo("이메일은 필수입니다.");
        }
    }

    @DisplayName("회원을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("모든 정보가 유효하면, 정상적으로 생성된다.")
        @Test
        void createsMember_whenAllFieldsAreValid() {
            // Arrange & Act
            Member member = Member.create(
                "testuser1", "Test1234!", "홍길동",
                LocalDate.of(1990, 1, 15), Gender.MALE, "test@example.com", "010-1234-5678",
                stubEncoder
            );

            // Assert
            assertAll(
                () -> assertThat(member.getLoginId()).isEqualTo("testuser1"),
                () -> assertThat(member.verifyPassword("Test1234!", stubEncoder)).isTrue(),
                () -> assertThat(member.getName()).isEqualTo("홍길동"),
                () -> assertThat(member.getBirthDate()).isEqualTo(LocalDate.of(1990, 1, 15)),
                () -> assertThat(member.getGender()).isEqualTo(Gender.MALE),
                () -> assertThat(member.getEmail()).isEqualTo("test@example.com"),
                () -> assertThat(member.getPhone()).isEqualTo("010-1234-5678")
            );
        }

        @DisplayName("전화번호 없이 생성하면, phone이 null이다.")
        @Test
        void createsMember_whenPhoneIsNull() {
            // Arrange & Act
            Member member = Member.create(
                "testuser1", "Test1234!", "홍길동",
                LocalDate.of(1990, 1, 15), Gender.FEMALE, "test@example.com", null,
                stubEncoder
            );

            // Assert
            assertAll(
                () -> assertThat(member.getGender()).isEqualTo(Gender.FEMALE),
                () -> assertThat(member.getPhone()).isNull()
            );
        }
    }

    @DisplayName("로그인ID 검증 시, ")
    @Nested
    class ValidateLoginId {

        @DisplayName("영문과 숫자 외 문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdContainsSpecialCharacters() {
            CoreException exception = assertThrows(CoreException.class, () -> {
                Member.create(
                    "test@user", "Test1234!", "홍길동",
                    LocalDate.of(1990, 1, 15), Gender.MALE, "test@example.com", null, stubEncoder
                );
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("비밀번호 검증 시, ")
    @Nested
    class ValidatePassword {

        @DisplayName("8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsTooShort() {
            CoreException exception = assertThrows(CoreException.class, () -> {
                Member.create(
                    "testuser1", "Test12!", "홍길동",
                    LocalDate.of(1990, 1, 15), Gender.MALE, "test@example.com", null, stubEncoder
                );
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("16자 초과이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsTooLong() {
            CoreException exception = assertThrows(CoreException.class, () -> {
                Member.create(
                    "testuser1", "Test1234!Test1234", "홍길동",
                    LocalDate.of(1990, 1, 15), Gender.MALE, "test@example.com", null, stubEncoder
                );
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("허용되지 않은 문자(한글)가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsInvalidCharacters() {
            CoreException exception = assertThrows(CoreException.class, () -> {
                Member.create(
                    "testuser1", "Test123한글!", "홍길동",
                    LocalDate.of(1990, 1, 15), Gender.MALE, "test@example.com", null, stubEncoder
                );
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthDate() {
            CoreException exception = assertThrows(CoreException.class, () -> {
                Member.create(
                    "testuser1", "Test19900115!", "홍길동",
                    LocalDate.of(1990, 1, 15), Gender.MALE, "test@example.com", null, stubEncoder
                );
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("이름 검증 시, ")
    @Nested
    class ValidateName {

        @DisplayName("한글과 영문이 혼합되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameContainsMixedLanguages() {
            CoreException exception = assertThrows(CoreException.class, () -> {
                Member.create(
                    "testuser1", "Test1234!", "Hong길동",
                    LocalDate.of(1990, 1, 15), Gender.MALE, "test@example.com", null, stubEncoder
                );
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("한글 이름에 공백이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenKoreanNameContainsSpace() {
            CoreException exception = assertThrows(CoreException.class, () -> {
                Member.create(
                    "testuser1", "Test1234!", "홍 길동",
                    LocalDate.of(1990, 1, 15), Gender.MALE, "test@example.com", null, stubEncoder
                );
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("영문 이름의 연속 공백은 하나로 정규화된다.")
        @Test
        void normalizesConsecutiveSpaces_whenEnglishNameHasMultipleSpaces() {
            Member member = Member.create(
                "testuser1", "Test1234!", "John  Doe",
                LocalDate.of(1990, 1, 15), Gender.MALE, "test@example.com", null, stubEncoder
            );
            assertThat(member.getName()).isEqualTo("John Doe");
        }
    }

    @DisplayName("이메일 검증 시, ")
    @Nested
    class ValidateEmail {

        @DisplayName("올바르지 않은 형식이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailFormatIsInvalid() {
            CoreException exception = assertThrows(CoreException.class, () -> {
                Member.create(
                    "testuser1", "Test1234!", "홍길동",
                    LocalDate.of(1990, 1, 15), Gender.MALE, "invalid-email", null, stubEncoder
                );
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("전화번호 검증 시, ")
    @Nested
    class ValidatePhone {

        @DisplayName("올바른 형식이면, 정상적으로 생성된다.")
        @Test
        void createsMember_whenPhoneFormatIsValid() {
            Member member = Member.create(
                "testuser1", "Test1234!", "홍길동",
                LocalDate.of(1990, 1, 15), Gender.MALE, "test@example.com", "010-1234-5678",
                stubEncoder
            );
            assertThat(member.getPhone()).isEqualTo("010-1234-5678");
        }

        @DisplayName("올바르지 않은 형식이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPhoneFormatIsInvalid() {
            CoreException exception = assertThrows(CoreException.class, () -> {
                Member.create(
                    "testuser1", "Test1234!", "홍길동",
                    LocalDate.of(1990, 1, 15), Gender.MALE, "test@example.com", "01012345678",
                    stubEncoder
                );
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(exception.getMessage()).isEqualTo("전화번호 형식이 올바르지 않습니다. (010-XXXX-XXXX)");
        }

        @DisplayName("하이픈 없는 번호이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPhoneHasNoHyphens() {
            CoreException exception = assertThrows(CoreException.class, () -> {
                Member.create(
                    "testuser1", "Test1234!", "홍길동",
                    LocalDate.of(1990, 1, 15), Gender.MALE, "test@example.com", "0101234567",
                    stubEncoder
                );
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("전화번호 수정 시, ")
    @Nested
    class UpdatePhone {

        @DisplayName("유효한 전화번호로 수정하면, 정상적으로 변경된다.")
        @Test
        void updatesPhone_whenValidFormat() {
            Member member = createDefaultMember();

            member.updatePhone("010-9999-8888");

            assertThat(member.getPhone()).isEqualTo("010-9999-8888");
        }

        @DisplayName("null로 수정하면, 전화번호가 삭제된다.")
        @Test
        void removesPhone_whenNull() {
            Member member = Member.create(
                "testuser1", "Test1234!", "홍길동",
                LocalDate.of(1990, 1, 15), Gender.MALE, "test@example.com", "010-1234-5678",
                stubEncoder
            );

            member.updatePhone(null);

            assertThat(member.getPhone()).isNull();
        }

        @DisplayName("올바르지 않은 형식이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenInvalidFormat() {
            Member member = createDefaultMember();

            CoreException exception = assertThrows(CoreException.class, () -> {
                member.updatePhone("invalid");
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("회원 탈퇴 시, ")
    @Nested
    class Withdraw {

        @DisplayName("비밀번호가 일치하면, soft delete 처리된다.")
        @Test
        void deletedMember_whenPasswordMatches() {
            Member member = createDefaultMember();

            member.withdraw("Test1234!", stubEncoder);

            assertThat(member.getDeletedAt()).isNotNull();
        }

        @DisplayName("비밀번호가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsNull() {
            Member member = createDefaultMember();

            CoreException exception = assertThrows(CoreException.class, () -> {
                member.withdraw(null, stubEncoder);
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(exception.getMessage()).isEqualTo("비밀번호는 필수입니다.");
        }

        @DisplayName("비밀번호가 일치하지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordDoesNotMatch() {
            Member member = createDefaultMember();

            CoreException exception = assertThrows(CoreException.class, () -> {
                member.withdraw("WrongPass1!", stubEncoder);
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(exception.getMessage()).isEqualTo("비밀번호가 일치하지 않습니다.");
        }
    }

    @DisplayName("비밀번호 변경 시, ")
    @Nested
    class ChangePassword {

        @DisplayName("현재 비밀번호가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCurrentPasswordIsNull() {
            Member member = createDefaultMember();

            CoreException exception = assertThrows(CoreException.class, () -> {
                member.changePassword(null, "NewPass5678!", stubEncoder);
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(exception.getMessage()).isEqualTo("현재 비밀번호는 필수입니다.");
        }

        @DisplayName("새 비밀번호가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordIsNull() {
            Member member = createDefaultMember();

            CoreException exception = assertThrows(CoreException.class, () -> {
                member.changePassword("Test1234!", null, stubEncoder);
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(exception.getMessage()).isEqualTo("새 비밀번호는 필수입니다.");
        }

        @DisplayName("현재 비밀번호가 일치하지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCurrentPasswordDoesNotMatch() {
            Member member = createDefaultMember();

            CoreException exception = assertThrows(CoreException.class, () -> {
                member.changePassword("WrongPass1!", "NewPass5678!", stubEncoder);
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(exception.getMessage()).isEqualTo("현재 비밀번호가 일치하지 않습니다.");
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 동일하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordIsSameAsCurrent() {
            Member member = createDefaultMember();

            CoreException exception = assertThrows(CoreException.class, () -> {
                member.changePassword("Test1234!", "Test1234!", stubEncoder);
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(exception.getMessage()).isEqualTo("새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }

        @DisplayName("새 비밀번호가 규칙을 위반하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordViolatesRules() {
            Member member = createDefaultMember();

            CoreException exception = assertThrows(CoreException.class, () -> {
                member.changePassword("Test1234!", "short", stubEncoder);
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호에 생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordContainsBirthDate() {
            Member member = createDefaultMember();

            CoreException exception = assertThrows(CoreException.class, () -> {
                member.changePassword("Test1234!", "Pass19900115!", stubEncoder);
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(exception.getMessage()).isEqualTo("비밀번호에 생년월일을 포함할 수 없습니다.");
        }

        @DisplayName("모든 조건이 유효하면, 비밀번호가 정상적으로 변경된다.")
        @Test
        void changesPassword_whenAllConditionsAreValid() {
            Member member = createDefaultMember();

            member.changePassword("Test1234!", "NewPass5678!", stubEncoder);

            assertThat(member.verifyPassword("NewPass5678!", stubEncoder)).isTrue();
        }
    }
}

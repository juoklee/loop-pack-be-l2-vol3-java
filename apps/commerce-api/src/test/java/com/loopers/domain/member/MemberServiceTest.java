package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemberServiceTest {

    private MemberService memberService;
    private FakeMemberReader fakeMemberReader;
    private FakeMemberRepository fakeMemberRepository;
    private StubPasswordEncoder stubPasswordEncoder;

    @BeforeEach
    void setUp() {
        fakeMemberReader = new FakeMemberReader();
        fakeMemberRepository = new FakeMemberRepository(fakeMemberReader);
        stubPasswordEncoder = new StubPasswordEncoder();
        memberService = new MemberService(fakeMemberReader, fakeMemberRepository, stubPasswordEncoder);
    }

    @DisplayName("회원가입 시, ")
    @Nested
    class Register {

        @DisplayName("이미 존재하는 로그인ID로 가입하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdAlreadyExists() {
            // Arrange
            String existingLoginId = "existingUser";
            fakeMemberReader.addExistingLoginId(existingLoginId);

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                memberService.register(
                    existingLoginId, "Test1234!", "홍길동",
                    LocalDate.of(1990, 1, 15), Gender.MALE, "test@example.com", null
                );
            });

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("유효한 정보로 가입하면, 회원이 저장된다.")
        @Test
        void savesMember_whenAllFieldsAreValid() {
            // Arrange
            String loginId = "newUser";
            String password = "Test1234!";
            String name = "홍길동";
            LocalDate birthDate = LocalDate.of(1990, 1, 15);
            String email = "test@example.com";

            // Act
            Member member = memberService.register(loginId, password, name, birthDate, Gender.MALE, email, null);

            // Assert
            assertAll(
                () -> assertThat(member.getLoginId()).isEqualTo(loginId),
                () -> assertThat(member.verifyPassword(password, stubPasswordEncoder)).isTrue(),
                () -> assertThat(member.getName()).isEqualTo(name),
                () -> assertThat(member.getBirthDate()).isEqualTo(birthDate),
                () -> assertThat(member.getGender()).isEqualTo(Gender.MALE),
                () -> assertThat(member.getEmail()).isEqualTo(email)
            );
        }
    }

    @DisplayName("전화번호 수정 시, ")
    @Nested
    class UpdatePhone {

        @DisplayName("유효한 전화번호로 수정하면, 정상적으로 변경된다.")
        @Test
        void updatesPhone_whenValidFormat() {
            // Arrange
            Member member = memberService.register(
                "testUser", "Test1234!", "홍길동",
                LocalDate.of(1990, 1, 15), Gender.MALE, "test@example.com", null
            );

            // Act
            memberService.updatePhone("testUser", "010-9999-8888");

            // Assert
            assertThat(member.getPhone()).isEqualTo("010-9999-8888");
        }
    }

    @DisplayName("회원 탈퇴 시, ")
    @Nested
    class Withdraw {

        @DisplayName("비밀번호가 일치하면, soft delete 처리된다.")
        @Test
        void deleteMember_whenPasswordMatches() {
            // Arrange
            Member member = memberService.register(
                "testUser", "Test1234!", "홍길동",
                LocalDate.of(1990, 1, 15), Gender.MALE, "test@example.com", null
            );

            // Act
            memberService.withdraw("testUser", "Test1234!");

            // Assert
            assertThat(member.getDeletedAt()).isNotNull();
        }

        @DisplayName("비밀번호가 일치하지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordDoesNotMatch() {
            // Arrange
            memberService.register(
                "testUser", "Test1234!", "홍길동",
                LocalDate.of(1990, 1, 15), Gender.MALE, "test@example.com", null
            );

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                memberService.withdraw("testUser", "WrongPass1!");
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    // Fake 구현체
    static class FakeMemberReader implements MemberReader {
        private final Map<String, Boolean> existingLoginIds = new HashMap<>();
        private final Map<String, Member> members = new HashMap<>();

        void addExistingLoginId(String loginId) {
            existingLoginIds.put(loginId, true);
        }

        void addMember(Member member) {
            members.put(member.getLoginId(), member);
        }

        @Override
        public boolean existsByLoginId(String loginId) {
            return existingLoginIds.containsKey(loginId);
        }

        @Override
        public Optional<Member> findByLoginId(String loginId) {
            return Optional.ofNullable(members.get(loginId));
        }

        @Override
        public Optional<Member> findById(Long id) {
            return Optional.empty();
        }

        @Override
        public Page<Member> findAll(String keyword, Pageable pageable) {
            return Page.empty();
        }
    }

    static class FakeMemberRepository implements MemberRepository {
        private final Map<Long, Member> members = new HashMap<>();
        private final FakeMemberReader fakeMemberReader;
        private long idSequence = 1L;

        FakeMemberRepository(FakeMemberReader fakeMemberReader) {
            this.fakeMemberReader = fakeMemberReader;
        }

        @Override
        public Member save(Member member) {
            members.put(idSequence++, member);
            fakeMemberReader.addMember(member);
            return member;
        }
    }

    static class StubPasswordEncoder implements PasswordEncoder {
        @Override
        public String encode(String rawPassword) {
            return "encoded_" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String encodedPassword) {
            return encodedPassword.equals("encoded_" + rawPassword);
        }
    }
}

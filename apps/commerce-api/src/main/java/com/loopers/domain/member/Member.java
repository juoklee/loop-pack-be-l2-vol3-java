package com.loopers.domain.member;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "member")
public class Member extends BaseEntity {

    @Column(unique = true, nullable = false)
    private String loginId;
    private String password;
    private String name;
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    private String email;
    private String phone;

    protected Member() {}

    private Member(String loginId, String password, String name,
                   LocalDate birthDate, Gender gender, String email, String phone) {
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthDate = birthDate;
        this.gender = gender;
        this.email = email;
        this.phone = phone;
    }

    public static Member create(String loginId, String rawPassword,
                                String name, LocalDate birthDate,
                                Gender gender, String email, String phone,
                                PasswordEncoder encoder) {
        validateNotBlank(loginId, "로그인ID는 필수입니다.");
        validateNotBlank(rawPassword, "비밀번호는 필수입니다.");
        validateNotBlank(name, "이름은 필수입니다.");
        validateNotNull(birthDate, "생년월일은 필수입니다.");
        validateNotNull(gender, "성별은 필수입니다.");
        validateNotBlank(email, "이메일은 필수입니다.");

        validateLoginId(loginId);
        validatePassword(rawPassword, birthDate);
        String normalizedName = normalizeName(name);
        validateName(normalizedName);
        validateEmail(email);
        if (phone != null && !phone.isBlank()) {
            validatePhone(phone);
        }

        String encodedPassword = encoder.encode(rawPassword);
        return new Member(loginId, encodedPassword, normalizedName, birthDate, gender, email,
            phone != null && !phone.isBlank() ? phone : null);
    }

    private static void validateNotNull(Object value, String message) {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, message);
        }
    }

    private static void validateNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, message);
        }
    }

    private static void validatePassword(String password, LocalDate birthDate) {
        if (password.length() < 8 || password.length() > 16) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자여야 합니다.");
        }
        if (!password.matches("^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]+$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 영문 대소문자, 숫자, 특수문자만 허용됩니다.");
        }
        String birthDateStr = birthDate.toString().replace("-", ""); // 19900115
        if (password.contains(birthDateStr)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }

    private static void validateLoginId(String loginId) {
        if (!loginId.matches("^[a-zA-Z0-9]+$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인ID는 영문과 숫자만 허용됩니다.");
        }
    }

    private static String normalizeName(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }

    private static void validateName(String name) {
        boolean isKorean = name.matches("^[가-힣]+$");
        boolean isEnglish = name.matches("^[a-zA-Z]+( [a-zA-Z]+)*$");

        if (!isKorean && !isEnglish) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 한글만 또는 영문만 허용됩니다.");
        }
    }

    private static void validateEmail(String email) {
        if (!email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "올바른 이메일 형식이 아닙니다.");
        }
    }

    private static void validatePhone(String phone) {
        if (!phone.matches("^010-\\d{4}-\\d{4}$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "전화번호 형식이 올바르지 않습니다. (010-XXXX-XXXX)");
        }
    }

    public void updatePhone(String phone) {
        if (phone != null && !phone.isBlank()) {
            validatePhone(phone);
            this.phone = phone;
        } else {
            this.phone = null;
        }
    }

    public void withdraw(String rawPassword, PasswordEncoder encoder) {
        validateNotBlank(rawPassword, "비밀번호는 필수입니다.");
        if (!encoder.matches(rawPassword, this.password)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호가 일치하지 않습니다.");
        }
        this.delete();
    }

    public void changePassword(String currentPassword, String newRawPassword,
                               PasswordEncoder encoder) {
        validateNotBlank(currentPassword, "현재 비밀번호는 필수입니다.");
        validateNotBlank(newRawPassword, "새 비밀번호는 필수입니다.");

        if (!encoder.matches(currentPassword, this.password)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다.");
        }
        if (encoder.matches(newRawPassword, this.password)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }
        validatePassword(newRawPassword, this.birthDate);
        this.password = encoder.encode(newRawPassword);
    }

    public boolean verifyPassword(String rawPassword, PasswordEncoder encoder) {
        return encoder.matches(rawPassword, this.password);
    }

    // Getter
    public String getLoginId() {
        return loginId;
    }

    public String getName() {
        return name;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public Gender getGender() {
        return gender;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }
}

package com.loopers.application.member;

import com.loopers.domain.member.Gender;
import com.loopers.domain.member.Member;

import java.time.LocalDate;

public record MemberInfo(String loginId, String name, LocalDate birthDate, Gender gender, String email, String phone) {
    public static MemberInfo from(Member member) {
        return new MemberInfo(
            member.getLoginId(),
            member.getName(),
            member.getBirthDate(),
            member.getGender(),
            member.getEmail(),
            member.getPhone()
        );
    }

    public static MemberInfo fromWithMaskedName(Member member) {
        return new MemberInfo(
            member.getLoginId(),
            maskName(member.getName()),
            member.getBirthDate(),
            member.getGender(),
            member.getEmail(),
            member.getPhone()
        );
    }

    private static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        if (name.length() == 1) {
            return "*";
        }
        return name.substring(0, name.length() - 1) + "*";
    }
}

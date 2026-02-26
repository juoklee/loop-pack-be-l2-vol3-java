package com.loopers.application.member;

import com.loopers.domain.member.Member;

import java.time.LocalDate;
import java.time.ZonedDateTime;

public record AdminMemberInfo(
    Long memberId,
    String loginId,
    String name,
    LocalDate birthDate,
    String gender,
    String email,
    String phone,
    ZonedDateTime createdAt
) {
    public static AdminMemberInfo from(Member member) {
        return new AdminMemberInfo(
            member.getId(),
            member.getLoginId(),
            member.getName(),
            member.getBirthDate(),
            member.getGender().name(),
            member.getEmail(),
            member.getPhone(),
            member.getCreatedAt()
        );
    }
}

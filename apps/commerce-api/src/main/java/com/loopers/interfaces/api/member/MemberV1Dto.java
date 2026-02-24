package com.loopers.interfaces.api.member;

import com.loopers.application.member.MemberInfo;

import java.time.LocalDate;

public class MemberV1Dto {

    public record RegisterRequest(
        String loginId,
        String password,
        String name,
        LocalDate birthDate,
        String gender,
        String email,
        String phone
    ) {}

    public record ChangePasswordRequest(
        String currentPassword,
        String newPassword
    ) {}

    public record UpdatePhoneRequest(
        String phone
    ) {}

    public record WithdrawRequest(
        String password
    ) {}

    public record MemberResponse(
        MemberDto member
    ) {
        public record MemberDto(
            String loginId,
            String name,
            LocalDate birthDate,
            String gender,
            String email,
            String phone
        ) {}

        public static MemberResponse from(MemberInfo info) {
            return new MemberResponse(
                new MemberDto(
                    info.loginId(),
                    info.name(),
                    info.birthDate(),
                    info.gender(),
                    info.email(),
                    info.phone()
                )
            );
        }
    }
}

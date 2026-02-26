package com.loopers.interfaces.api.member;

import com.loopers.application.PagedInfo;
import com.loopers.application.member.AdminMemberInfo;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

public class MemberAdminV1Dto {

    public record MemberResponse(MemberDto member) {
        public record MemberDto(
            Long id,
            String loginId,
            String name,
            LocalDate birthDate,
            String gender,
            String email,
            String phone,
            ZonedDateTime createdAt
        ) {}

        public static MemberResponse from(AdminMemberInfo info) {
            return new MemberResponse(
                new MemberDto(
                    info.memberId(),
                    info.loginId(),
                    info.name(),
                    info.birthDate(),
                    info.gender(),
                    info.email(),
                    info.phone(),
                    info.createdAt()
                )
            );
        }
    }

    public record MemberListResponse(List<MemberDto> members, PageInfo page) {
        public record MemberDto(
            Long id,
            String loginId,
            String name,
            LocalDate birthDate,
            String gender,
            String email,
            String phone,
            ZonedDateTime createdAt
        ) {}

        public record PageInfo(long totalElements, int totalPages, int page, int size) {}

        public static MemberListResponse from(PagedInfo<AdminMemberInfo> result) {
            List<MemberDto> members = result.content().stream()
                .map(info -> new MemberDto(
                    info.memberId(),
                    info.loginId(),
                    info.name(),
                    info.birthDate(),
                    info.gender(),
                    info.email(),
                    info.phone(),
                    info.createdAt()
                ))
                .toList();
            return new MemberListResponse(
                members,
                new PageInfo(result.totalElements(), result.totalPages(), result.page(), result.size())
            );
        }
    }
}

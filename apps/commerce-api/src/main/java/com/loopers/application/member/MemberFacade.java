package com.loopers.application.member;

import com.loopers.application.PagedInfo;
import com.loopers.domain.PageResult;
import com.loopers.domain.member.Gender;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class MemberFacade {

    private final MemberService memberService;

    public MemberInfo register(String loginId, String rawPassword, String name,
                               LocalDate birthDate, String gender, String email, String phone) {
        Gender genderEnum = Gender.valueOf(gender);
        Member member = memberService.register(loginId, rawPassword, name, birthDate, genderEnum, email, phone);
        return MemberInfo.from(member);
    }

    public MemberInfo getMe(String loginId) {
        Member member = memberService.getMemberByLoginId(loginId);
        return MemberInfo.fromWithMaskedName(member);
    }

    public void changePassword(String loginId, String currentPassword, String newPassword) {
        memberService.changePassword(loginId, currentPassword, newPassword);
    }

    public void updatePhone(String loginId, String phone) {
        memberService.updatePhone(loginId, phone);
    }

    public void withdraw(String loginId, String rawPassword) {
        memberService.withdraw(loginId, rawPassword);
    }

    public PagedInfo<AdminMemberInfo> getMembersForAdmin(String keyword, int page, int size) {
        PageResult<Member> result = memberService.getMembers(keyword, page, size);
        return new PagedInfo<>(
            result.content().stream().map(AdminMemberInfo::from).toList(),
            result.totalElements(),
            result.totalPages(),
            result.page(),
            result.size()
        );
    }

    public AdminMemberInfo getMemberForAdmin(Long memberId) {
        Member member = memberService.getMember(memberId);
        return AdminMemberInfo.from(member);
    }
}

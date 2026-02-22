package com.loopers.application.member;

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
                               LocalDate birthDate, Gender gender, String email, String phone) {
        Member member = memberService.register(loginId, rawPassword, name, birthDate, gender, email, phone);
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
}

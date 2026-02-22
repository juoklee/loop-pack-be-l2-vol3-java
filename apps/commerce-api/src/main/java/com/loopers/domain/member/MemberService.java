package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class MemberService {

    private final MemberReader memberReader;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Member register(String loginId, String rawPassword, String name,
                           LocalDate birthDate, String email) {
        if (memberReader.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 존재하는 로그인ID입니다.");
        }

        Member member = Member.create(loginId, rawPassword, name, birthDate, email, passwordEncoder);
        return memberRepository.save(member);
    }

    @Transactional
    public void changePassword(Member member, String currentPassword, String newPassword) {
        member.changePassword(currentPassword, newPassword, passwordEncoder);
        memberRepository.save(member);
    }
}

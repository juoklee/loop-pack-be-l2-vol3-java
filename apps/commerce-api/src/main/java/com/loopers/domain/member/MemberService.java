package com.loopers.domain.member;

import com.loopers.domain.PageResult;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
                           LocalDate birthDate, Gender gender, String email, String phone) {
        if (memberReader.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 존재하는 로그인ID입니다.");
        }

        Member member = Member.create(loginId, rawPassword, name, birthDate, gender, email, phone, passwordEncoder);
        return memberRepository.save(member);
    }

    @Transactional
    public void changePassword(String loginId, String currentPassword, String newPassword) {
        Member member = getMemberByLoginId(loginId);
        member.changePassword(currentPassword, newPassword, passwordEncoder);
    }

    @Transactional
    public void updatePhone(String loginId, String phone) {
        Member member = getMemberByLoginId(loginId);
        member.updatePhone(phone);
    }

    @Transactional
    public void withdraw(String loginId, String rawPassword) {
        Member member = getMemberByLoginId(loginId);
        member.withdraw(rawPassword, passwordEncoder);
    }

    @Transactional(readOnly = true)
    public Member getMemberByLoginId(String loginId) {
        return memberReader.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Member getMember(Long memberId) {
        return memberReader.findById(memberId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public PageResult<Member> getMembers(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Member> result = memberReader.findAll(keyword, pageable);
        return new PageResult<>(
            result.getContent(),
            result.getTotalElements(),
            result.getTotalPages(),
            result.getNumber(),
            result.getSize()
        );
    }
}

package com.loopers.domain.member;

import com.loopers.domain.PageResult;

import java.util.Optional;

public interface MemberReader {
    boolean existsByLoginId(String loginId);
    Optional<Member> findByLoginId(String loginId);
    Optional<Member> findById(Long id);
    PageResult<Member> findAll(String keyword, int page, int size);
}

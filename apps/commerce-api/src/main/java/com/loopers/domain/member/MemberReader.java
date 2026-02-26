package com.loopers.domain.member;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface MemberReader {
    boolean existsByLoginId(String loginId);
    Optional<Member> findByLoginId(String loginId);
    Optional<Member> findById(Long id);
    Page<Member> findAll(String keyword, Pageable pageable);
}

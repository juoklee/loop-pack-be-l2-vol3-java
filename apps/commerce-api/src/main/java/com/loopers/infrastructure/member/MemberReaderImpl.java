package com.loopers.infrastructure.member;

import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberReader;
import com.loopers.domain.member.QMember;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class MemberReaderImpl implements MemberReader {

    private final MemberJpaRepository memberJpaRepository;
    private final JPAQueryFactory queryFactory;

    @Override
    public boolean existsByLoginId(String loginId) {
        return memberJpaRepository.existsByLoginId(loginId);
    }

    @Override
    public Optional<Member> findByLoginId(String loginId) {
        return memberJpaRepository.findByLoginIdAndDeletedAtIsNull(loginId);
    }

    @Override
    public Optional<Member> findById(Long id) {
        return memberJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Page<Member> findAll(String keyword, Pageable pageable) {
        QMember member = QMember.member;

        BooleanBuilder where = new BooleanBuilder();
        where.and(member.deletedAt.isNull());

        if (keyword != null && !keyword.isBlank()) {
            where.and(
                member.loginId.containsIgnoreCase(keyword)
                    .or(member.name.containsIgnoreCase(keyword))
                    .or(member.email.containsIgnoreCase(keyword))
            );
        }

        List<Member> content = queryFactory.selectFrom(member)
            .where(where)
            .orderBy(member.createdAt.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        Long total = queryFactory.select(member.count())
            .from(member)
            .where(where)
            .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }
}

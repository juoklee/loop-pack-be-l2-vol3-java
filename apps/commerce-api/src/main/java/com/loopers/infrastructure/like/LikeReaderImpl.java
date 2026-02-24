package com.loopers.infrastructure.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeReader;
import com.loopers.domain.like.LikeTargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class LikeReaderImpl implements LikeReader {
    private final LikeJpaRepository likeJpaRepository;

    @Override
    public Optional<Like> findByMemberIdAndTargetTypeAndTargetId(Long memberId, LikeTargetType targetType, Long targetId) {
        return likeJpaRepository.findByMemberIdAndTargetTypeAndTargetId(memberId, targetType, targetId);
    }

    @Override
    public Page<Like> findAllByMemberIdAndTargetType(Long memberId, LikeTargetType targetType, Pageable pageable) {
        return likeJpaRepository.findAllByMemberIdAndTargetType(memberId, targetType, pageable);
    }
}

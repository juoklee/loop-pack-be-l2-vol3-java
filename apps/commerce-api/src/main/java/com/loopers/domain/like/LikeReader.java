package com.loopers.domain.like;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface LikeReader {
    Optional<Like> findByMemberIdAndTargetTypeAndTargetId(Long memberId, LikeTargetType targetType, Long targetId);
    Page<Like> findAllByMemberIdAndTargetType(Long memberId, LikeTargetType targetType, Pageable pageable);
}

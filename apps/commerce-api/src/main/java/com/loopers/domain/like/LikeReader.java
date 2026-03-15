package com.loopers.domain.like;

import com.loopers.domain.PageResult;

import java.util.List;
import java.util.Optional;

public interface LikeReader {
    Optional<Like> findByMemberIdAndTargetTypeAndTargetId(Long memberId, LikeTargetType targetType, Long targetId);
    PageResult<Like> findAllByMemberIdAndTargetType(Long memberId, LikeTargetType targetType, int page, int size);
    int countByTargetTypeAndTargetId(LikeTargetType targetType, Long targetId);
    List<LikeCountProjection> countAllByTargetType(LikeTargetType targetType);
}

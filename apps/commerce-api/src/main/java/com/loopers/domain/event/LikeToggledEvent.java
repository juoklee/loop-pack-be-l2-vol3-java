package com.loopers.domain.event;

import com.loopers.domain.like.LikeTargetType;

public record LikeToggledEvent(
    Long memberId,
    LikeTargetType targetType,
    Long targetId,
    boolean liked
) {}

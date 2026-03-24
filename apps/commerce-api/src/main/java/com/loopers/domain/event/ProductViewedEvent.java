package com.loopers.domain.event;

public record ProductViewedEvent(
    Long memberId,
    Long productId
) {}

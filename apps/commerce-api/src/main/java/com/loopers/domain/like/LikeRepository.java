package com.loopers.domain.like;

public interface LikeRepository {
    Like save(Like like);
    void delete(Like like);
}

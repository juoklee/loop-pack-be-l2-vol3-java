package com.loopers.infrastructure.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LikeJpaRepository extends JpaRepository<Like, Long> {
    Optional<Like> findByMemberIdAndTargetTypeAndTargetId(Long memberId, LikeTargetType targetType, Long targetId);
    Page<Like> findAllByMemberIdAndTargetType(Long memberId, LikeTargetType targetType, Pageable pageable);
    int countByTargetTypeAndTargetId(LikeTargetType targetType, Long targetId);

    @Query("SELECT l.targetId, COUNT(l) FROM Like l WHERE l.targetType = :targetType GROUP BY l.targetId")
    List<Object[]> countGroupByTargetType(@Param("targetType") LikeTargetType targetType);
}

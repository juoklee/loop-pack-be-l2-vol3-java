package com.loopers.domain.like;

import com.loopers.domain.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeReader likeReader;
    private final LikeRepository likeRepository;

    @Transactional
    public boolean toggleLike(Long memberId, LikeTargetType targetType, Long targetId) {
        Optional<Like> existing = likeReader.findByMemberIdAndTargetTypeAndTargetId(memberId, targetType, targetId);

        if (existing.isPresent()) {
            likeRepository.delete(existing.get());
            return false;
        }

        Like like = Like.create(memberId, targetType, targetId);
        likeRepository.save(like);
        return true;
    }

    @Transactional(readOnly = true)
    public PageResult<Like> getMyLikes(Long memberId, LikeTargetType targetType, int page, int size) {
        return likeReader.findAllByMemberIdAndTargetType(memberId, targetType, page, size);
    }

    @Transactional(readOnly = true)
    public int countLikes(LikeTargetType targetType, Long targetId) {
        return likeReader.countByTargetTypeAndTargetId(targetType, targetId);
    }

    @Transactional(readOnly = true)
    public List<LikeCountProjection> countAllLikes(LikeTargetType targetType) {
        return likeReader.countAllByTargetType(targetType);
    }
}

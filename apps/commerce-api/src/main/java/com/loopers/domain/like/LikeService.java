package com.loopers.domain.like;

import com.loopers.domain.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
        Pageable pageable = PageRequest.of(page, size);
        Page<Like> result = likeReader.findAllByMemberIdAndTargetType(memberId, targetType, pageable);
        return new PageResult<>(
            result.getContent(),
            result.getTotalElements(),
            result.getTotalPages(),
            result.getNumber(),
            result.getSize()
        );
    }
}

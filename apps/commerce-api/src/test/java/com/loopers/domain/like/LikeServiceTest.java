package com.loopers.domain.like;

import com.loopers.domain.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.loopers.domain.PageResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LikeServiceTest {

    private LikeService likeService;
    private FakeLikeReader fakeLikeReader;
    private FakeLikeRepository fakeLikeRepository;

    @BeforeEach
    void setUp() {
        fakeLikeReader = new FakeLikeReader();
        fakeLikeRepository = new FakeLikeRepository();
        likeService = new LikeService(fakeLikeReader, fakeLikeRepository);
    }

    @DisplayName("좋아요를 토글할 때, ")
    @Nested
    class ToggleLike {

        @DisplayName("좋아요가 없으면, 좋아요를 생성하고 true를 반환한다.")
        @Test
        void returnsTrue_whenLikeNotExists() {
            // Act
            boolean result = likeService.toggleLike(1L, LikeTargetType.PRODUCT, 100L);

            // Assert
            assertThat(result).isTrue();
            assertThat(fakeLikeRepository.getSavedLikes()).hasSize(1);
        }

        @DisplayName("좋아요가 이미 있으면, 좋아요를 삭제하고 false를 반환한다.")
        @Test
        void returnsFalse_whenLikeAlreadyExists() {
            // Arrange
            Like existingLike = Like.create(1L, LikeTargetType.PRODUCT, 100L);
            fakeLikeReader.addLike(existingLike);
            fakeLikeRepository.addLike(existingLike);

            // Act
            boolean result = likeService.toggleLike(1L, LikeTargetType.PRODUCT, 100L);

            // Assert
            assertThat(result).isFalse();
            assertThat(fakeLikeRepository.getDeletedLikes()).hasSize(1);
        }

        @DisplayName("상품 좋아요와 브랜드 좋아요는 독립적으로 동작한다.")
        @Test
        void operatesIndependently_whenDifferentTargetTypes() {
            // Act
            boolean productResult = likeService.toggleLike(1L, LikeTargetType.PRODUCT, 100L);
            boolean brandResult = likeService.toggleLike(1L, LikeTargetType.BRAND, 100L);

            // Assert
            assertThat(productResult).isTrue();
            assertThat(brandResult).isTrue();
            assertThat(fakeLikeRepository.getSavedLikes()).hasSize(2);
        }
    }

    @DisplayName("내 좋아요 목록을 조회할 때, ")
    @Nested
    class GetMyLikes {

        @DisplayName("좋아요한 항목이 있으면, 페이징된 결과를 반환한다.")
        @Test
        void returnsPagedResult_whenLikesExist() {
            // Arrange
            fakeLikeReader.addLike(Like.create(1L, LikeTargetType.PRODUCT, 100L));
            fakeLikeReader.addLike(Like.create(1L, LikeTargetType.PRODUCT, 200L));
            fakeLikeReader.addLike(Like.create(1L, LikeTargetType.BRAND, 50L));

            // Act
            PageResult<Like> result = likeService.getMyLikes(1L, LikeTargetType.PRODUCT, 0, 20);

            // Assert
            assertThat(result.content()).hasSize(2);
            assertThat(result.totalElements()).isEqualTo(2);
        }

        @DisplayName("좋아요한 항목이 없으면, 빈 결과를 반환한다.")
        @Test
        void returnsEmptyResult_whenNoLikes() {
            // Act
            PageResult<Like> result = likeService.getMyLikes(1L, LikeTargetType.PRODUCT, 0, 20);

            // Assert
            assertThat(result.content()).isEmpty();
            assertThat(result.totalElements()).isZero();
        }
    }

    // Fake 구현체
    static class FakeLikeReader implements LikeReader {
        private final List<Like> likes = new ArrayList<>();

        void addLike(Like like) {
            likes.add(like);
        }

        @Override
        public Optional<Like> findByMemberIdAndTargetTypeAndTargetId(Long memberId, LikeTargetType targetType, Long targetId) {
            return likes.stream()
                .filter(l -> l.getMemberId().equals(memberId)
                    && l.getTargetType() == targetType
                    && l.getTargetId().equals(targetId))
                .findFirst();
        }

        @Override
        public PageResult<Like> findAllByMemberIdAndTargetType(Long memberId, LikeTargetType targetType, int page, int size) {
            List<Like> filtered = likes.stream()
                .filter(l -> l.getMemberId().equals(memberId) && l.getTargetType() == targetType)
                .toList();
            return new PageResult<>(filtered, filtered.size(), 1, page, size);
        }

        @Override
        public int countByTargetTypeAndTargetId(LikeTargetType targetType, Long targetId) {
            return (int) likes.stream()
                .filter(l -> l.getTargetType() == targetType && l.getTargetId().equals(targetId))
                .count();
        }

        @Override
        public List<LikeCountProjection> countAllByTargetType(LikeTargetType targetType) {
            return likes.stream()
                .filter(l -> l.getTargetType() == targetType)
                .collect(java.util.stream.Collectors.groupingBy(Like::getTargetId, java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .map(e -> new LikeCountProjection(e.getKey(), e.getValue()))
                .toList();
        }
    }

    static class FakeLikeRepository implements LikeRepository {
        private final List<Like> savedLikes = new ArrayList<>();
        private final List<Like> deletedLikes = new ArrayList<>();

        void addLike(Like like) {
            savedLikes.add(like);
        }

        List<Like> getSavedLikes() {
            return savedLikes;
        }

        List<Like> getDeletedLikes() {
            return deletedLikes;
        }

        @Override
        public Like save(Like like) {
            savedLikes.add(like);
            return like;
        }

        @Override
        public void delete(Like like) {
            deletedLikes.add(like);
            savedLikes.remove(like);
        }
    }
}

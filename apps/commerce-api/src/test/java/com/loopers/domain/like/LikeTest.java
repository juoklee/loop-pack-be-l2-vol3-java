package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LikeTest {

    @DisplayName("좋아요를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("모든 필드가 유효하면, 정상적으로 생성된다.")
        @Test
        void createsLike_whenAllFieldsAreValid() {
            // Arrange & Act
            Like like = Like.create(1L, LikeTargetType.PRODUCT, 100L);

            // Assert
            assertAll(
                () -> assertThat(like.getMemberId()).isEqualTo(1L),
                () -> assertThat(like.getTargetType()).isEqualTo(LikeTargetType.PRODUCT),
                () -> assertThat(like.getTargetId()).isEqualTo(100L)
            );
        }

        @DisplayName("BRAND 타입으로도 정상적으로 생성된다.")
        @Test
        void createsLike_whenTargetTypeIsBrand() {
            // Arrange & Act
            Like like = Like.create(1L, LikeTargetType.BRAND, 50L);

            // Assert
            assertAll(
                () -> assertThat(like.getMemberId()).isEqualTo(1L),
                () -> assertThat(like.getTargetType()).isEqualTo(LikeTargetType.BRAND),
                () -> assertThat(like.getTargetId()).isEqualTo(50L)
            );
        }

        @DisplayName("memberId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenMemberIdIsNull() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                Like.create(null, LikeTargetType.PRODUCT, 100L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("targetType이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenTargetTypeIsNull() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                Like.create(1L, null, 100L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("targetId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenTargetIdIsNull() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                Like.create(1L, LikeTargetType.PRODUCT, null)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}

package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrandTest {

    @DisplayName("브랜드를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("이름과 설명이 유효하면, 정상적으로 생성된다.")
        @Test
        void createsBrand_whenAllFieldsAreValid() {
            // Arrange
            String name = "Nike";
            String description = "Just Do It";

            // Act
            Brand brand = Brand.create(name, description);

            // Assert
            assertAll(
                () -> assertThat(brand.getName()).isEqualTo(name),
                () -> assertThat(brand.getDescription()).isEqualTo(description),
                () -> assertThat(brand.getLikeCount()).isZero()
            );
        }

        @DisplayName("설명이 null이어도, 정상적으로 생성된다.")
        @Test
        void createsBrand_whenDescriptionIsNull() {
            // Arrange
            String name = "Nike";

            // Act
            Brand brand = Brand.create(name, null);

            // Assert
            assertAll(
                () -> assertThat(brand.getName()).isEqualTo(name),
                () -> assertThat(brand.getDescription()).isNull()
            );
        }

        @DisplayName("이름이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Brand.create(null, "설명");
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Brand.create("  ", "설명");
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("브랜드를 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("이름과 설명을 변경하면, 정상적으로 수정된다.")
        @Test
        void updatesBrand_whenFieldsAreValid() {
            // Arrange
            Brand brand = Brand.create("Nike", "Just Do It");

            // Act
            brand.updateInfo("Adidas", "Impossible Is Nothing");

            // Assert
            assertAll(
                () -> assertThat(brand.getName()).isEqualTo("Adidas"),
                () -> assertThat(brand.getDescription()).isEqualTo("Impossible Is Nothing")
            );
        }

        @DisplayName("이름이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            // Arrange
            Brand brand = Brand.create("Nike", "Just Do It");

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                brand.updateInfo(null, "설명");
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // Arrange
            Brand brand = Brand.create("Nike", "Just Do It");

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                brand.updateInfo("  ", "설명");
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("좋아요 수를 관리할 때, ")
    @Nested
    class LikeCount {

        @DisplayName("좋아요를 증가시키면, 1 증가한다.")
        @Test
        void increasesLikeCount_whenCalled() {
            // Arrange
            Brand brand = Brand.create("Nike", "Just Do It");

            // Act
            brand.increaseLikeCount();

            // Assert
            assertThat(brand.getLikeCount()).isEqualTo(1);
        }

        @DisplayName("좋아요를 연속으로 증가시키면, 호출 횟수만큼 증가한다.")
        @Test
        void increasesLikeCount_whenCalledMultipleTimes() {
            // Arrange
            Brand brand = Brand.create("Nike", "Just Do It");

            // Act
            brand.increaseLikeCount();
            brand.increaseLikeCount();
            brand.increaseLikeCount();

            // Assert
            assertThat(brand.getLikeCount()).isEqualTo(3);
        }

        @DisplayName("좋아요를 감소시키면, 1 감소한다.")
        @Test
        void decreasesLikeCount_whenCalled() {
            // Arrange
            Brand brand = Brand.create("Nike", "Just Do It");
            brand.increaseLikeCount();

            // Act
            brand.decreaseLikeCount();

            // Assert
            assertThat(brand.getLikeCount()).isZero();
        }

        @DisplayName("좋아요가 0일 때 감소시키면, 0 미만으로 내려가지 않는다.")
        @Test
        void doesNotGoBelowZero_whenDecreasedAtZero() {
            // Arrange
            Brand brand = Brand.create("Nike", "Just Do It");

            // Act
            brand.decreaseLikeCount();

            // Assert
            assertThat(brand.getLikeCount()).isZero();
        }
    }
}

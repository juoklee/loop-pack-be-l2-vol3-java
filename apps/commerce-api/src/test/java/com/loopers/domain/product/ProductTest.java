package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductTest {

    @DisplayName("상품을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("모든 필드가 유효하면, 정상적으로 생성된다.")
        @Test
        void createsProduct_whenAllFieldsAreValid() {
            // Arrange & Act
            Product product = Product.create(1L, "에어맥스 90", "나이키 클래식 운동화", 139000L, 100, 5);

            // Assert
            assertAll(
                () -> assertThat(product.getBrandId()).isEqualTo(1L),
                () -> assertThat(product.getName()).isEqualTo("에어맥스 90"),
                () -> assertThat(product.getDescription()).isEqualTo("나이키 클래식 운동화"),
                () -> assertThat(product.getPrice()).isEqualTo(139000L),
                () -> assertThat(product.getStockQuantity()).isEqualTo(100),
                () -> assertThat(product.getMaxOrderQuantity()).isEqualTo(5),
                () -> assertThat(product.getLikeCount()).isZero()
            );
        }

        @DisplayName("설명이 null이어도, 정상적으로 생성된다.")
        @Test
        void createsProduct_whenDescriptionIsNull() {
            // Arrange & Act
            Product product = Product.create(1L, "에어맥스 90", null, 139000L, 100, 5);

            // Assert
            assertThat(product.getDescription()).isNull();
        }

        @DisplayName("이름이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                Product.create(1L, null, "설명", 139000L, 100, 5)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                Product.create(1L, "  ", "설명", 139000L, 100, 5)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPriceIsZero() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                Product.create(1L, "에어맥스 90", "설명", 0L, 100, 5)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPriceIsNegative() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                Product.create(1L, "에어맥스 90", "설명", -1000L, 100, 5)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("재고 수량이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStockQuantityIsNegative() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                Product.create(1L, "에어맥스 90", "설명", 139000L, -1, 5)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("재고 수량이 0이면, 정상적으로 생성된다.")
        @Test
        void createsProduct_whenStockQuantityIsZero() {
            // Arrange & Act
            Product product = Product.create(1L, "에어맥스 90", "설명", 139000L, 0, 5);

            // Assert
            assertThat(product.getStockQuantity()).isZero();
        }

        @DisplayName("최대 주문 수량이 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenMaxOrderQuantityIsZero() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                Product.create(1L, "에어맥스 90", "설명", 139000L, 100, 0)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("최대 주문 수량이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenMaxOrderQuantityIsNegative() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                Product.create(1L, "에어맥스 90", "설명", 139000L, 100, -1)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("상품을 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("유효한 정보로 수정하면, 정상적으로 수정된다.")
        @Test
        void updatesProduct_whenFieldsAreValid() {
            // Arrange
            Product product = Product.create(1L, "에어맥스 90", "설명", 139000L, 100, 5);

            // Act
            product.updateInfo("에어맥스 95", "업데이트된 설명", 149000L, 3);

            // Assert
            assertAll(
                () -> assertThat(product.getName()).isEqualTo("에어맥스 95"),
                () -> assertThat(product.getDescription()).isEqualTo("업데이트된 설명"),
                () -> assertThat(product.getPrice()).isEqualTo(149000L),
                () -> assertThat(product.getMaxOrderQuantity()).isEqualTo(3),
                () -> assertThat(product.getBrandId()).isEqualTo(1L) // 브랜드 변경 불가
            );
        }

        @DisplayName("이름이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            // Arrange
            Product product = Product.create(1L, "에어맥스 90", "설명", 139000L, 100, 5);

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                product.updateInfo(null, "설명", 149000L, 3)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPriceIsZero() {
            // Arrange
            Product product = Product.create(1L, "에어맥스 90", "설명", 139000L, 100, 5);

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                product.updateInfo("에어맥스 95", "설명", 0L, 3)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("최대 주문 수량이 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenMaxOrderQuantityIsZero() {
            // Arrange
            Product product = Product.create(1L, "에어맥스 90", "설명", 139000L, 100, 5);

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                product.updateInfo("에어맥스 95", "설명", 149000L, 0)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고를 관리할 때, ")
    @Nested
    class Stock {

        @DisplayName("재고를 직접 설정하면(updateStock), 설정한 값으로 변경된다.")
        @Test
        void updatesStock_whenQuantityIsValid() {
            // Arrange
            Product product = Product.create(1L, "에어맥스 90", "설명", 139000L, 100, 5);

            // Act
            product.updateStock(200);

            // Assert
            assertThat(product.getStockQuantity()).isEqualTo(200);
        }

        @DisplayName("재고를 0으로 설정하면(updateStock), 정상적으로 변경된다.")
        @Test
        void updatesStock_whenQuantityIsZero() {
            // Arrange
            Product product = Product.create(1L, "에어맥스 90", "설명", 139000L, 100, 5);

            // Act
            product.updateStock(0);

            // Assert
            assertThat(product.getStockQuantity()).isZero();
        }

        @DisplayName("재고를 음수로 설정하면(updateStock), BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUpdateStockIsNegative() {
            // Arrange
            Product product = Product.create(1L, "에어맥스 90", "설명", 139000L, 100, 5);

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                product.updateStock(-1)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("재고를 차감하면(decreaseStock), 차감된 값으로 변경된다.")
        @Test
        void decreasesStock_whenSufficientQuantity() {
            // Arrange
            Product product = Product.create(1L, "에어맥스 90", "설명", 139000L, 100, 5);

            // Act
            product.decreaseStock(30);

            // Assert
            assertThat(product.getStockQuantity()).isEqualTo(70);
        }

        @DisplayName("재고와 동일한 수량을 차감하면(decreaseStock), 재고가 0이 된다.")
        @Test
        void decreasesStockToZero_whenQuantityEqualsStock() {
            // Arrange
            Product product = Product.create(1L, "에어맥스 90", "설명", 139000L, 100, 5);

            // Act
            product.decreaseStock(100);

            // Assert
            assertThat(product.getStockQuantity()).isZero();
        }

        @DisplayName("재고보다 많은 수량을 차감하면(decreaseStock), BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDecreaseStockExceedsAvailable() {
            // Arrange
            Product product = Product.create(1L, "에어맥스 90", "설명", 139000L, 100, 5);

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                product.decreaseStock(101)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("재고를 증가시키면(increaseStock), 증가된 값으로 변경된다.")
        @Test
        void increasesStock_whenQuantityIsPositive() {
            // Arrange
            Product product = Product.create(1L, "에어맥스 90", "설명", 139000L, 100, 5);

            // Act
            product.increaseStock(50);

            // Assert
            assertThat(product.getStockQuantity()).isEqualTo(150);
        }

        @DisplayName("재고를 0으로 증가시키면(increaseStock), BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenIncreaseStockIsZero() {
            // Arrange
            Product product = Product.create(1L, "에어맥스 90", "설명", 139000L, 100, 5);

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                product.increaseStock(0)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("재고를 음수로 증가시키면(increaseStock), BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenIncreaseStockIsNegative() {
            // Arrange
            Product product = Product.create(1L, "에어맥스 90", "설명", 139000L, 100, 5);

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                product.increaseStock(-1)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("주문 수량을 검증할 때, ")
    @Nested
    class ValidateOrderQuantity {

        @DisplayName("최대 주문 수량 이하이면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenQuantityIsWithinLimit() {
            // Arrange
            Product product = Product.create(1L, "에어맥스 90", "설명", 139000L, 100, 5);

            // Act & Assert (no exception)
            product.validateOrderQuantity(5);
        }

        @DisplayName("최대 주문 수량을 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityExceedsMax() {
            // Arrange
            Product product = Product.create(1L, "에어맥스 90", "설명", 139000L, 100, 5);

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                product.validateOrderQuantity(6)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("좋아요 수를 관리할 때, ")
    @Nested
    class LikeCount {

        @DisplayName("좋아요 수를 증가시키면, 1 증가한다.")
        @Test
        void increasesLikeCount() {
            // Arrange
            Product product = Product.create(1L, "에어맥스 90", "설명", 139000L, 100, 5);

            // Act
            product.increaseLikeCount();

            // Assert
            assertThat(product.getLikeCount()).isEqualTo(1);
        }

        @DisplayName("좋아요 수를 감소시키면, 1 감소한다.")
        @Test
        void decreasesLikeCount() {
            // Arrange
            Product product = Product.create(1L, "에어맥스 90", "설명", 139000L, 100, 5);
            product.increaseLikeCount();
            product.increaseLikeCount();

            // Act
            product.decreaseLikeCount();

            // Assert
            assertThat(product.getLikeCount()).isEqualTo(1);
        }

        @DisplayName("좋아요 수가 0일 때 감소시키면, 0을 유지한다.")
        @Test
        void keepsZero_whenDecreasingFromZero() {
            // Arrange
            Product product = Product.create(1L, "에어맥스 90", "설명", 139000L, 100, 5);

            // Act
            product.decreaseLikeCount();

            // Assert
            assertThat(product.getLikeCount()).isZero();
        }
    }
}

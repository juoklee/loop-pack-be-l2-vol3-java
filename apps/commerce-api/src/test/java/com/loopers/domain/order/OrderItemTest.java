package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderItemTest {

    @DisplayName("주문 항목을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("모든 필드가 유효하면, 정상적으로 생성된다.")
        @Test
        void createsOrderItem_whenAllFieldsAreValid() {
            // Arrange & Act
            OrderItem item = OrderItem.create(1L, 10L, "에어맥스 90", 139000L, 2);

            // Assert
            assertAll(
                () -> assertThat(item.getOrderId()).isEqualTo(1L),
                () -> assertThat(item.getProductId()).isEqualTo(10L),
                () -> assertThat(item.getProductName()).isEqualTo("에어맥스 90"),
                () -> assertThat(item.getProductPrice()).isEqualTo(139000L),
                () -> assertThat(item.getQuantity()).isEqualTo(2)
            );
        }

        @DisplayName("quantity가 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsZero() {
            CoreException exception = assertThrows(CoreException.class, () ->
                OrderItem.create(1L, 10L, "에어맥스 90", 139000L, 0)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("quantity가 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNegative() {
            CoreException exception = assertThrows(CoreException.class, () ->
                OrderItem.create(1L, 10L, "에어맥스 90", 139000L, -1)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("productName이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductNameIsBlank() {
            CoreException exception = assertThrows(CoreException.class, () ->
                OrderItem.create(1L, 10L, "", 139000L, 2)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("productPrice가 0 이하이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductPriceIsNotPositive() {
            CoreException exception = assertThrows(CoreException.class, () ->
                OrderItem.create(1L, 10L, "에어맥스 90", 0L, 2)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("소계를 계산할 때, ")
    @Nested
    class GetSubtotal {

        @DisplayName("productPrice * quantity를 반환한다.")
        @Test
        void returnsProductPriceTimesQuantity() {
            // Arrange
            OrderItem item = OrderItem.create(1L, 10L, "에어맥스 90", 139000L, 3);

            // Act
            Long subtotal = item.getSubtotal();

            // Assert
            assertThat(subtotal).isEqualTo(417000L);
        }
    }
}

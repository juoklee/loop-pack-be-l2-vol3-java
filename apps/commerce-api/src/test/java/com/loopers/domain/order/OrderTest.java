package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderTest {

    @DisplayName("주문을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("모든 필드가 유효하면, PENDING_PAYMENT 상태로 생성된다.")
        @Test
        void createsOrder_withPendingPaymentStatus() {
            // Arrange & Act
            Order order = Order.create(
                1L, "홍길동", "010-1234-5678", "12345",
                "서울시 강남구 테헤란로 123", "101동 202호", 258000L
            );

            // Assert
            assertAll(
                () -> assertThat(order.getMemberId()).isEqualTo(1L),
                () -> assertThat(order.getRecipientName()).isEqualTo("홍길동"),
                () -> assertThat(order.getRecipientPhone()).isEqualTo("010-1234-5678"),
                () -> assertThat(order.getZipCode()).isEqualTo("12345"),
                () -> assertThat(order.getAddress1()).isEqualTo("서울시 강남구 테헤란로 123"),
                () -> assertThat(order.getAddress2()).isEqualTo("101동 202호"),
                () -> assertThat(order.getTotalAmount()).isEqualTo(258000L),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT)
            );
        }

        @DisplayName("address2가 null이어도 정상 생성된다.")
        @Test
        void createsOrder_whenAddress2IsNull() {
            // Arrange & Act
            Order order = Order.create(
                1L, "홍길동", "010-1234-5678", "12345",
                "서울시 강남구 테헤란로 123", null, 100000L
            );

            // Assert
            assertThat(order.getAddress2()).isNull();
        }

        @DisplayName("memberId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenMemberIdIsNull() {
            CoreException exception = assertThrows(CoreException.class, () ->
                Order.create(null, "홍길동", "010-1234-5678", "12345", "주소", null, 100000L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("recipientName이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenRecipientNameIsBlank() {
            CoreException exception = assertThrows(CoreException.class, () ->
                Order.create(1L, "", "010-1234-5678", "12345", "주소", null, 100000L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("totalAmount가 0 이하이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenTotalAmountIsNotPositive() {
            CoreException exception = assertThrows(CoreException.class, () ->
                Order.create(1L, "홍길동", "010-1234-5678", "12345", "주소", null, 0L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("쿠폰을 적용한 주문이면, 쿠폰 관련 필드가 설정된다.")
        @Test
        void createsOrder_withCouponFields() {
            // Arrange & Act
            Order order = Order.create(
                1L, "홍길동", "010-1234-5678", "12345",
                "서울시 강남구", null, 95000L,
                10L, 100000L, 5000L
            );

            // Assert
            assertAll(
                () -> assertThat(order.getTotalAmount()).isEqualTo(95000L),
                () -> assertThat(order.getMemberCouponId()).isEqualTo(10L),
                () -> assertThat(order.getOriginalAmount()).isEqualTo(100000L),
                () -> assertThat(order.getDiscountAmount()).isEqualTo(5000L)
            );
        }

        @DisplayName("쿠폰 적용 시 originalAmount가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCouponAppliedButOriginalAmountIsNull() {
            CoreException exception = assertThrows(CoreException.class, () ->
                Order.create(1L, "홍길동", "010-1234-5678", "12345", "주소", null,
                             95000L, 10L, null, 5000L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("쿠폰 적용 시 discountAmount가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCouponAppliedButDiscountAmountIsNull() {
            CoreException exception = assertThrows(CoreException.class, () ->
                Order.create(1L, "홍길동", "010-1234-5678", "12345", "주소", null,
                             95000L, 10L, 100000L, null)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("쿠폰 적용 시 totalAmount가 originalAmount - discountAmount와 일치하지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenTotalAmountMismatchWithDiscount() {
            CoreException exception = assertThrows(CoreException.class, () ->
                Order.create(1L, "홍길동", "010-1234-5678", "12345", "주소", null,
                             90000L, 10L, 100000L, 5000L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("쿠폰 미적용 주문이면, 쿠폰 관련 필드가 null이다.")
        @Test
        void createsOrder_withoutCouponFields() {
            // Arrange & Act
            Order order = Order.create(
                1L, "홍길동", "010-1234-5678", "12345", "주소", null, 100000L
            );

            // Assert
            assertAll(
                () -> assertThat(order.getMemberCouponId()).isNull(),
                () -> assertThat(order.getOriginalAmount()).isNull(),
                () -> assertThat(order.getDiscountAmount()).isNull()
            );
        }
    }

    @DisplayName("결제 완료할 때, ")
    @Nested
    class CompletePayment {

        @DisplayName("PENDING_PAYMENT 상태이면, COMPLETED로 변경된다.")
        @Test
        void completesPayment_whenPendingPayment() {
            // Arrange
            Order order = Order.create(
                1L, "홍길동", "010-1234-5678", "12345", "주소", null, 100000L
            );

            // Act
            order.completePayment();

            // Assert
            assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        }

        @DisplayName("COMPLETED 상태이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAlreadyCompleted() {
            // Arrange
            Order order = Order.create(
                1L, "홍길동", "010-1234-5678", "12345", "주소", null, 100000L
            );
            order.completePayment();

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, order::completePayment);
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("결제 실패할 때, ")
    @Nested
    class FailPayment {

        @DisplayName("PENDING_PAYMENT 상태이면, PAYMENT_FAILED로 변경된다.")
        @Test
        void failsPayment_whenPendingPayment() {
            // Arrange
            Order order = Order.create(
                1L, "홍길동", "010-1234-5678", "12345", "주소", null, 100000L
            );

            // Act
            order.failPayment();

            // Assert
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        }

        @DisplayName("COMPLETED 상태이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAlreadyCompleted() {
            // Arrange
            Order order = Order.create(
                1L, "홍길동", "010-1234-5678", "12345", "주소", null, 100000L
            );
            order.completePayment();

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, order::failPayment);
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("주문을 취소할 때, ")
    @Nested
    class Cancel {

        @DisplayName("PENDING_PAYMENT 상태이면, CANCELLED로 변경된다.")
        @Test
        void cancelsOrder_whenPendingPayment() {
            // Arrange
            Order order = Order.create(
                1L, "홍길동", "010-1234-5678", "12345", "주소", null, 100000L
            );

            // Act
            order.cancel();

            // Assert
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @DisplayName("COMPLETED 상태이면, CANCELLED로 변경된다.")
        @Test
        void cancelsOrder_whenStatusIsCompleted() {
            // Arrange
            Order order = Order.create(
                1L, "홍길동", "010-1234-5678", "12345", "주소", null, 100000L
            );
            order.completePayment();

            // Act
            order.cancel();

            // Assert
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @DisplayName("이미 CANCELLED 상태이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAlreadyCancelled() {
            // Arrange
            Order order = Order.create(
                1L, "홍길동", "010-1234-5678", "12345", "주소", null, 100000L
            );
            order.cancel();

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, order::cancel);
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("PAYMENT_FAILED 상태이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPaymentFailed() {
            // Arrange
            Order order = Order.create(
                1L, "홍길동", "010-1234-5678", "12345", "주소", null, 100000L
            );
            order.failPayment();

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, order::cancel);
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("배송지를 수정할 때, ")
    @Nested
    class UpdateShippingAddress {

        @DisplayName("COMPLETED 상태이면, 배송지 스냅샷이 수정된다.")
        @Test
        void updatesAddress_whenStatusIsCompleted() {
            // Arrange
            Order order = Order.create(
                1L, "홍길동", "010-1234-5678", "12345", "기존 주소", null, 100000L
            );
            order.completePayment();

            // Act
            order.updateShippingAddress("김철수", "010-9999-9999", "67890", "새 주소", "301동");

            // Assert
            assertAll(
                () -> assertThat(order.getRecipientName()).isEqualTo("김철수"),
                () -> assertThat(order.getRecipientPhone()).isEqualTo("010-9999-9999"),
                () -> assertThat(order.getZipCode()).isEqualTo("67890"),
                () -> assertThat(order.getAddress1()).isEqualTo("새 주소"),
                () -> assertThat(order.getAddress2()).isEqualTo("301동")
            );
        }

        @DisplayName("PENDING_PAYMENT 상태이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStatusIsPendingPayment() {
            // Arrange
            Order order = Order.create(
                1L, "홍길동", "010-1234-5678", "12345", "주소", null, 100000L
            );

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                order.updateShippingAddress("김철수", "010-9999-9999", "67890", "새 주소", null)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("CANCELLED 상태이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStatusIsCancelled() {
            // Arrange
            Order order = Order.create(
                1L, "홍길동", "010-1234-5678", "12345", "주소", null, 100000L
            );
            order.cancel();

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                order.updateShippingAddress("김철수", "010-9999-9999", "67890", "새 주소", null)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}

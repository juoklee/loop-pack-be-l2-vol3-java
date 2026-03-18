package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentTest {

    @DisplayName("결제를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("모든 필드가 유효하면, REQUESTED 상태로 생성된다.")
        @Test
        void createsPayment_withRequestedStatus() {
            Payment payment = Payment.create(1L, 100L, "SAMSUNG", "1234-5678-9012-3456", 50000L);

            assertAll(
                () -> assertThat(payment.getMemberId()).isEqualTo(1L),
                () -> assertThat(payment.getOrderId()).isEqualTo(100L),
                () -> assertThat(payment.getCardType()).isEqualTo("SAMSUNG"),
                () -> assertThat(payment.getCardNo()).isEqualTo("1234-5678-9012-3456"),
                () -> assertThat(payment.getAmount()).isEqualTo(50000L),
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED),
                () -> assertThat(payment.getTransactionKey()).isNull(),
                () -> assertThat(payment.getFailReason()).isNull()
            );
        }

        @DisplayName("memberId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenMemberIdIsNull() {
            CoreException exception = assertThrows(CoreException.class, () ->
                Payment.create(null, 100L, "SAMSUNG", "1234-5678-9012-3456", 50000L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("orderId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOrderIdIsNull() {
            CoreException exception = assertThrows(CoreException.class, () ->
                Payment.create(1L, null, "SAMSUNG", "1234-5678-9012-3456", 50000L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("cardType이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCardTypeIsBlank() {
            CoreException exception = assertThrows(CoreException.class, () ->
                Payment.create(1L, 100L, "", "1234-5678-9012-3456", 50000L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("amount가 0 이하이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAmountIsNotPositive() {
            CoreException exception = assertThrows(CoreException.class, () ->
                Payment.create(1L, 100L, "SAMSUNG", "1234-5678-9012-3456", 0L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("PG 처리 시작할 때, ")
    @Nested
    class MarkProcessing {

        @DisplayName("REQUESTED 상태이면, PROCESSING으로 변경되고 transactionKey가 설정된다.")
        @Test
        void marksProcessing_whenRequested() {
            Payment payment = Payment.create(1L, 100L, "SAMSUNG", "1234-5678-9012-3456", 50000L);

            payment.markProcessing("txn_abc123");

            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING),
                () -> assertThat(payment.getTransactionKey()).isEqualTo("txn_abc123")
            );
        }

        @DisplayName("PROCESSING 상태이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAlreadyProcessing() {
            Payment payment = Payment.create(1L, 100L, "SAMSUNG", "1234-5678-9012-3456", 50000L);
            payment.markProcessing("txn_abc123");

            CoreException exception = assertThrows(CoreException.class, () ->
                payment.markProcessing("txn_other")
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("transactionKey가 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenTransactionKeyIsBlank() {
            Payment payment = Payment.create(1L, 100L, "SAMSUNG", "1234-5678-9012-3456", 50000L);

            CoreException exception = assertThrows(CoreException.class, () ->
                payment.markProcessing("")
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("결제 완료할 때, ")
    @Nested
    class Complete {

        @DisplayName("PROCESSING 상태이면, COMPLETED로 변경된다.")
        @Test
        void completesPayment_whenProcessing() {
            Payment payment = Payment.create(1L, 100L, "SAMSUNG", "1234-5678-9012-3456", 50000L);
            payment.markProcessing("txn_abc123");

            payment.complete();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @DisplayName("TIMEOUT 상태이면, COMPLETED로 변경된다 (늦은 콜백 복원).")
        @Test
        void completesPayment_whenTimeout() {
            Payment payment = Payment.create(1L, 100L, "SAMSUNG", "1234-5678-9012-3456", 50000L);
            payment.markProcessing("txn_abc123");
            payment.timeout();

            payment.complete();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @DisplayName("REQUESTED 상태이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenRequested() {
            Payment payment = Payment.create(1L, 100L, "SAMSUNG", "1234-5678-9012-3456", 50000L);

            CoreException exception = assertThrows(CoreException.class, payment::complete);
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("결제 실패할 때, ")
    @Nested
    class Fail {

        @DisplayName("PROCESSING 상태이면, FAILED로 변경되고 실패 사유가 설정된다.")
        @Test
        void failsPayment_whenProcessing() {
            Payment payment = Payment.create(1L, 100L, "SAMSUNG", "1234-5678-9012-3456", 50000L);
            payment.markProcessing("txn_abc123");

            payment.fail("한도 초과");

            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(payment.getFailReason()).isEqualTo("한도 초과")
            );
        }

        @DisplayName("REQUESTED 상태이면, FAILED로 변경된다 (PG 요청 자체 실패).")
        @Test
        void failsPayment_whenRequested() {
            Payment payment = Payment.create(1L, 100L, "SAMSUNG", "1234-5678-9012-3456", 50000L);

            payment.fail("PG 요청 타임아웃");

            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(payment.getFailReason()).isEqualTo("PG 요청 타임아웃")
            );
        }

        @DisplayName("TIMEOUT 상태이면, FAILED로 변경된다 (늦은 콜백 확정 실패).")
        @Test
        void failsPayment_whenTimeout() {
            Payment payment = Payment.create(1L, 100L, "SAMSUNG", "1234-5678-9012-3456", 50000L);
            payment.markProcessing("txn_abc123");
            payment.timeout();

            payment.fail("한도 초과");

            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(payment.getFailReason()).isEqualTo("한도 초과")
            );
        }

        @DisplayName("이미 COMPLETED 상태이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAlreadyCompleted() {
            Payment payment = Payment.create(1L, 100L, "SAMSUNG", "1234-5678-9012-3456", 50000L);
            payment.markProcessing("txn_abc123");
            payment.complete();

            CoreException exception = assertThrows(CoreException.class, () ->
                payment.fail("실패")
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("타임아웃 처리할 때, ")
    @Nested
    class Timeout {

        @DisplayName("PROCESSING 상태이면, TIMEOUT으로 변경된다.")
        @Test
        void timesOut_whenProcessing() {
            Payment payment = Payment.create(1L, 100L, "SAMSUNG", "1234-5678-9012-3456", 50000L);
            payment.markProcessing("txn_abc123");

            payment.timeout();

            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.TIMEOUT),
                () -> assertThat(payment.getFailReason()).isEqualTo("PG 응답 타임아웃")
            );
        }

        @DisplayName("REQUESTED 상태이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenRequested() {
            Payment payment = Payment.create(1L, 100L, "SAMSUNG", "1234-5678-9012-3456", 50000L);

            CoreException exception = assertThrows(CoreException.class, payment::timeout);
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이미 COMPLETED 상태이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCompleted() {
            Payment payment = Payment.create(1L, 100L, "SAMSUNG", "1234-5678-9012-3456", 50000L);
            payment.markProcessing("txn_abc123");
            payment.complete();

            CoreException exception = assertThrows(CoreException.class, payment::timeout);
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("isTerminal 검증")
    @Nested
    class IsTerminal {

        @DisplayName("COMPLETED 상태이면, true를 반환한다.")
        @Test
        void returnsTrue_whenCompleted() {
            assertThat(PaymentStatus.COMPLETED.isTerminal()).isTrue();
        }

        @DisplayName("FAILED 상태이면, true를 반환한다.")
        @Test
        void returnsTrue_whenFailed() {
            assertThat(PaymentStatus.FAILED.isTerminal()).isTrue();
        }

        @DisplayName("REQUESTED 상태이면, false를 반환한다.")
        @Test
        void returnsFalse_whenRequested() {
            assertThat(PaymentStatus.REQUESTED.isTerminal()).isFalse();
        }

        @DisplayName("PROCESSING 상태이면, false를 반환한다.")
        @Test
        void returnsFalse_whenProcessing() {
            assertThat(PaymentStatus.PROCESSING.isTerminal()).isFalse();
        }

        @DisplayName("TIMEOUT 상태이면, false를 반환한다.")
        @Test
        void returnsFalse_whenTimeout() {
            assertThat(PaymentStatus.TIMEOUT.isTerminal()).isFalse();
        }
    }
}

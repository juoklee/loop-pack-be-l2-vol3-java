package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @InjectMocks
    private PaymentService paymentService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentReader paymentReader;

    @DisplayName("결제를 생성할 때, ")
    @Nested
    class CreatePayment {

        @DisplayName("활성 결제가 없으면, REQUESTED 상태로 생성된다.")
        @Test
        void createsPayment_whenNoActivePaymentExists() {
            given(paymentReader.findActiveByOrderId(100L)).willReturn(Optional.empty());
            given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

            Payment payment = paymentService.createPayment(1L, 100L, "SAMSUNG", "1234-5678-9012-3456", 50000L);

            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED),
                () -> assertThat(payment.getMemberId()).isEqualTo(1L),
                () -> assertThat(payment.getOrderId()).isEqualTo(100L)
            );
            verify(paymentRepository).save(any(Payment.class));
        }

        @DisplayName("이미 활성 결제가 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenActivePaymentExists() {
            Payment existing = Payment.create(1L, 100L, "SAMSUNG", "1234-5678-9012-3456", 50000L);
            given(paymentReader.findActiveByOrderId(100L)).willReturn(Optional.of(existing));

            CoreException exception = assertThrows(CoreException.class, () ->
                paymentService.createPayment(1L, 100L, "SAMSUNG", "1234-5678-9012-3456", 50000L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("결제를 조회할 때, ")
    @Nested
    class GetPayment {

        @DisplayName("ID로 조회하면 결제가 반환된다.")
        @Test
        void returnsPayment_whenFoundById() {
            Payment payment = Payment.create(1L, 100L, "SAMSUNG", "1234-5678-9012-3456", 50000L);
            given(paymentReader.findById(1L)).willReturn(Optional.of(payment));

            Payment found = paymentService.getPayment(1L);

            assertThat(found).isEqualTo(payment);
        }

        @DisplayName("ID로 조회 시 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotFoundById() {
            given(paymentReader.findById(999L)).willReturn(Optional.empty());

            CoreException exception = assertThrows(CoreException.class, () ->
                paymentService.getPayment(999L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("transactionKey로 조회하면 결제가 반환된다.")
        @Test
        void returnsPayment_whenFoundByTransactionKey() {
            Payment payment = Payment.create(1L, 100L, "SAMSUNG", "1234-5678-9012-3456", 50000L);
            payment.markProcessing("txn_abc");
            given(paymentReader.findByTransactionKey("txn_abc")).willReturn(Optional.of(payment));

            Payment found = paymentService.getPaymentByTransactionKey("txn_abc");

            assertThat(found.getTransactionKey()).isEqualTo("txn_abc");
        }

        @DisplayName("transactionKey로 조회 시 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotFoundByTransactionKey() {
            given(paymentReader.findByTransactionKey("unknown")).willReturn(Optional.empty());

            CoreException exception = assertThrows(CoreException.class, () ->
                paymentService.getPaymentByTransactionKey("unknown")
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}

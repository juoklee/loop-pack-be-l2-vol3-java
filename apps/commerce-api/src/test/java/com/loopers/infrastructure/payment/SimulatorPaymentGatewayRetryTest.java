package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentGatewayResponse;
import com.loopers.infrastructure.payment.dto.PgPaymentRequest;
import com.loopers.infrastructure.payment.dto.PgPaymentResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@DisplayName("SimulatorPaymentGateway Retry 통합 테스트")
class SimulatorPaymentGatewayRetryTest {

    @Autowired
    private SimulatorPaymentGateway simulatorPaymentGateway;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @MockBean
    private PgPaymentClient pgPaymentClient;

    @BeforeEach
    void resetCircuitBreaker() {
        circuitBreakerRegistry.getAllCircuitBreakers()
            .forEach(cb -> cb.reset());
    }

    private static final Long MEMBER_ID = 1L;
    private static final String ORDER_ID = "000001";
    private static final String CARD_TYPE = "SAMSUNG";
    private static final String CARD_NO = "1234-5678-9012-3456";
    private static final Long AMOUNT = 50000L;

    @Nested
    @DisplayName("SocketTimeoutException 발생 시 재시도")
    class SocketTimeoutRetry {

        @Test
        @DisplayName("첫 번째 호출에서 SocketTimeoutException 발생 후 재시도에서 성공한다")
        void retryOnSocketTimeoutThenSuccess() {
            // given
            given(pgPaymentClient.requestPayment(anyLong(), any(PgPaymentRequest.class)))
                .willThrow(new ResourceAccessException("I/O error", new SocketTimeoutException("Read timed out")))
                .willReturn(new PgPaymentResponse("txn-retry-001", "PENDING", null));

            // when
            PaymentGatewayResponse result = simulatorPaymentGateway.requestPayment(
                MEMBER_ID, ORDER_ID, CARD_TYPE, CARD_NO, AMOUNT
            );

            // then
            assertThat(result).isNotNull();
            assertThat(result.transactionKey()).isEqualTo("txn-retry-001");
            assertThat(result.status()).isEqualTo("PENDING");
            verify(pgPaymentClient, times(2)).requestPayment(anyLong(), any(PgPaymentRequest.class));
        }
    }

    @Nested
    @DisplayName("ResourceAccessException 발생 시 재시도")
    class ResourceAccessRetry {

        @Test
        @DisplayName("첫 번째 호출에서 ResourceAccessException 발생 후 재시도에서 성공한다")
        void retryOnResourceAccessThenSuccess() {
            // given
            given(pgPaymentClient.requestPayment(anyLong(), any(PgPaymentRequest.class)))
                .willThrow(new ResourceAccessException("Connection refused"))
                .willReturn(new PgPaymentResponse("txn-retry-002", "PENDING", null));

            // when
            PaymentGatewayResponse result = simulatorPaymentGateway.requestPayment(
                MEMBER_ID, ORDER_ID, CARD_TYPE, CARD_NO, AMOUNT
            );

            // then
            assertThat(result).isNotNull();
            assertThat(result.transactionKey()).isEqualTo("txn-retry-002");
            verify(pgPaymentClient, times(2)).requestPayment(anyLong(), any(PgPaymentRequest.class));
        }
    }

    @Nested
    @DisplayName("HttpServerErrorException 발생 시 재시도")
    class HttpServerErrorRetry {

        @Test
        @DisplayName("첫 번째 호출에서 HttpServerErrorException 발생 후 재시도에서 성공한다")
        void retryOnHttpServerErrorThenSuccess() {
            // given
            given(pgPaymentClient.requestPayment(anyLong(), any(PgPaymentRequest.class)))
                .willThrow(HttpServerErrorException.create(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Internal Server Error", null, null, null))
                .willReturn(new PgPaymentResponse("txn-retry-003", "PENDING", null));

            // when
            PaymentGatewayResponse result = simulatorPaymentGateway.requestPayment(
                MEMBER_ID, ORDER_ID, CARD_TYPE, CARD_NO, AMOUNT
            );

            // then
            assertThat(result).isNotNull();
            assertThat(result.transactionKey()).isEqualTo("txn-retry-003");
            verify(pgPaymentClient, times(2)).requestPayment(anyLong(), any(PgPaymentRequest.class));
        }
    }

    @Nested
    @DisplayName("max-attempts 초과 시 fallback")
    class MaxAttemptsExceeded {

        @Test
        @DisplayName("모든 시도가 실패하면 fallback으로 null을 반환한다")
        void fallbackAfterAllRetriesExhausted() {
            // given
            given(pgPaymentClient.requestPayment(anyLong(), any(PgPaymentRequest.class)))
                .willThrow(new ResourceAccessException("Connection refused"));

            // when
            PaymentGatewayResponse result = simulatorPaymentGateway.requestPayment(
                MEMBER_ID, ORDER_ID, CARD_TYPE, CARD_NO, AMOUNT
            );

            // then — max-attempts: 2이므로 총 2회 호출 후 fallback (null 반환)
            assertThat(result).isNull();
            verify(pgPaymentClient, times(2)).requestPayment(anyLong(), any(PgPaymentRequest.class));
        }
    }

    @Nested
    @DisplayName("CoreException은 재시도하지 않는다")
    class NoRetryOnCoreException {

        @Test
        @DisplayName("CoreException 발생 시 재시도 없이 1회만 호출되고 fallback으로 null을 반환한다")
        void noRetryOnCoreException() {
            // given
            given(pgPaymentClient.requestPayment(anyLong(), any(PgPaymentRequest.class)))
                .willThrow(new CoreException(ErrorType.BAD_REQUEST, "잘못된 요청"));

            // when
            // CoreException은 ignore-exceptions에 포함되어 재시도하지 않지만,
            // @Retry fallbackMethod가 예외를 받아 null을 반환한다
            PaymentGatewayResponse result = simulatorPaymentGateway.requestPayment(
                MEMBER_ID, ORDER_ID, CARD_TYPE, CARD_NO, AMOUNT
            );

            // then — 재시도 없이 1회만 호출됨을 검증
            assertThat(result).isNull();
            verify(pgPaymentClient, times(1)).requestPayment(anyLong(), any(PgPaymentRequest.class));
        }
    }
}

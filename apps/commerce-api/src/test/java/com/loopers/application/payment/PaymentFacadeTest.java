package com.loopers.application.payment;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayResponse;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentFacadeTest {

    @InjectMocks
    private PaymentFacade paymentFacade;

    @Mock private MemberService memberService;
    @Mock private OrderService orderService;
    @Mock private PaymentService paymentService;
    @Mock private ProductService productService;
    @Mock private CouponService couponService;
    @Mock private PaymentGateway paymentGateway;

    private static final String LOGIN_ID = "testuser";
    private static final Long MEMBER_ID = 1L;
    private static final Long ORDER_ID = 10L;
    private static final Long PAYMENT_ID = 100L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentFacade, "self", paymentFacade);
    }

    private void stubMember() {
        Member member = mock(Member.class);
        given(member.getId()).willReturn(MEMBER_ID);
        given(memberService.getMemberByLoginId(LOGIN_ID)).willReturn(member);
    }

    @Nested
    @DisplayName("createPayment")
    class CreatePayment {

        @Test
        @DisplayName("결제 대기 주문에 대해 결제를 생성한다")
        void success() {
            // given
            stubMember();
            Order order = mock(Order.class);
            given(order.getStatus()).willReturn(OrderStatus.PENDING_PAYMENT);
            given(order.getTotalAmount()).willReturn(50000L);
            given(orderService.getOrderForMember(ORDER_ID, MEMBER_ID)).willReturn(order);

            Payment payment = Payment.create(MEMBER_ID, ORDER_ID, "SAMSUNG", "1234-5678-9012-3456", 50000L);
            given(paymentService.createPayment(MEMBER_ID, ORDER_ID, "SAMSUNG", "1234-5678-9012-3456", 50000L))
                .willReturn(payment);

            // when
            PaymentInfo result = paymentFacade.createPayment(LOGIN_ID, ORDER_ID, "SAMSUNG", "1234-5678-9012-3456");

            // then
            assertThat(result.orderId()).isEqualTo(ORDER_ID);
            assertThat(result.status()).isEqualTo("REQUESTED");
            assertThat(result.cardType()).isEqualTo("SAMSUNG");
        }

        @Test
        @DisplayName("결제 대기 상태가 아닌 주문은 결제할 수 없다")
        void failWhenOrderNotPendingPayment() {
            // given
            stubMember();
            Order order = mock(Order.class);
            given(order.getStatus()).willReturn(OrderStatus.COMPLETED);
            given(orderService.getOrderForMember(ORDER_ID, MEMBER_ID)).willReturn(order);

            // when & then
            assertThatThrownBy(() ->
                paymentFacade.createPayment(LOGIN_ID, ORDER_ID, "SAMSUNG", "1234-5678-9012-3456")
            ).isInstanceOf(CoreException.class);
        }
    }

    @Nested
    @DisplayName("executePayment")
    class ExecutePayment {

        @Test
        @DisplayName("PG 응답이 PENDING이면 PROCESSING 상태로 전이한다")
        void pendingResponse() {
            // given
            stubMember();
            Payment payment = Payment.create(MEMBER_ID, ORDER_ID, "SAMSUNG", "1234-5678-9012-3456", 50000L);
            given(paymentService.getPaymentForUpdate(PAYMENT_ID)).willReturn(payment);
            given(paymentService.getPayment(PAYMENT_ID)).willReturn(payment);

            PaymentGatewayResponse pgResponse = new PaymentGatewayResponse("txn-001", String.valueOf(ORDER_ID), "PENDING", null);
            given(paymentGateway.requestPayment(eq(MEMBER_ID), anyString(), anyString(), anyString(), anyLong()))
                .willReturn(pgResponse);

            // when
            PaymentInfo result = paymentFacade.executePayment(LOGIN_ID, PAYMENT_ID);

            // then
            assertThat(result.status()).isEqualTo("PROCESSING");
            assertThat(result.transactionKey()).isEqualTo("txn-001");
        }

        @Test
        @DisplayName("PG 응답이 null이면 (CircuitBreaker fallback) 실패 처리 및 보상한다")
        void nullResponseFallback() {
            // given
            stubMember();
            Payment payment = Payment.create(MEMBER_ID, ORDER_ID, "SAMSUNG", "1234-5678-9012-3456", 50000L);
            given(paymentService.getPaymentForUpdate(PAYMENT_ID)).willReturn(payment);
            given(paymentService.getPayment(PAYMENT_ID)).willReturn(payment);

            given(paymentGateway.requestPayment(eq(MEMBER_ID), anyString(), anyString(), anyString(), anyLong()))
                .willReturn(null);

            Order order = mock(Order.class);
            given(orderService.getOrder(ORDER_ID)).willReturn(order);
            given(orderService.getOrderItems(ORDER_ID)).willReturn(List.of());
            given(order.getMemberCouponId()).willReturn(null);

            // when
            PaymentInfo result = paymentFacade.executePayment(LOGIN_ID, PAYMENT_ID);

            // then
            assertThat(result.status()).isEqualTo("FAILED");
            verify(order).failPayment();
        }

        @Test
        @DisplayName("PG 응답이 FAILED이면 실패 처리 및 보상한다")
        void failedResponse() {
            // given
            stubMember();
            Payment payment = Payment.create(MEMBER_ID, ORDER_ID, "SAMSUNG", "1234-5678-9012-3456", 50000L);
            given(paymentService.getPaymentForUpdate(PAYMENT_ID)).willReturn(payment);
            given(paymentService.getPayment(PAYMENT_ID)).willReturn(payment);

            PaymentGatewayResponse pgResponse = new PaymentGatewayResponse("txn-001", String.valueOf(ORDER_ID), "FAILED", "한도 초과");
            given(paymentGateway.requestPayment(eq(MEMBER_ID), anyString(), anyString(), anyString(), anyLong()))
                .willReturn(pgResponse);

            Order order = mock(Order.class);
            given(orderService.getOrder(ORDER_ID)).willReturn(order);
            given(orderService.getOrderItems(ORDER_ID)).willReturn(List.of());
            given(order.getMemberCouponId()).willReturn(null);

            // when
            PaymentInfo result = paymentFacade.executePayment(LOGIN_ID, PAYMENT_ID);

            // then
            assertThat(result.status()).isEqualTo("FAILED");
            assertThat(result.failReason()).isEqualTo("한도 초과");
            verify(order).failPayment();
        }

        @Test
        @DisplayName("PG 응답이 FAILED이면 재고와 쿠폰을 복원한다")
        void failedResponseCompensatesStockAndCoupon() {
            // given
            stubMember();
            Payment payment = Payment.create(MEMBER_ID, ORDER_ID, "SAMSUNG", "1234-5678-9012-3456", 50000L);
            given(paymentService.getPaymentForUpdate(PAYMENT_ID)).willReturn(payment);
            given(paymentService.getPayment(PAYMENT_ID)).willReturn(payment);

            PaymentGatewayResponse pgResponse = new PaymentGatewayResponse("txn-001", String.valueOf(ORDER_ID), "FAILED", "잘못된 카드");
            given(paymentGateway.requestPayment(eq(MEMBER_ID), anyString(), anyString(), anyString(), anyLong()))
                .willReturn(pgResponse);

            Order order = mock(Order.class);
            given(orderService.getOrder(ORDER_ID)).willReturn(order);
            given(order.getMemberCouponId()).willReturn(5L);

            OrderItem item = mock(OrderItem.class);
            given(item.getProductId()).willReturn(1L);
            given(item.getQuantity()).willReturn(2);
            given(orderService.getOrderItems(ORDER_ID)).willReturn(List.of(item));

            Product product = mock(Product.class);
            given(productService.getProductForUpdate(1L)).willReturn(product);

            // when
            paymentFacade.executePayment(LOGIN_ID, PAYMENT_ID);

            // then
            verify(product).increaseStock(2);
            verify(couponService).restoreCoupon(5L);
        }
    }

    @Nested
    @DisplayName("processCallback")
    class ProcessCallback {

        @Test
        @DisplayName("성공 콜백 수신 시 결제 완료 및 주문 완료 처리한다")
        void successCallback() {
            // given
            Payment payment = Payment.create(MEMBER_ID, ORDER_ID, "SAMSUNG", "1234-5678-9012-3456", 50000L);
            payment.startExecution();
            payment.markProcessing("txn-001");
            given(paymentService.getPaymentByTransactionKeyForUpdate("txn-001")).willReturn(payment);

            Order order = mock(Order.class);
            given(orderService.getOrder(ORDER_ID)).willReturn(order);

            // when
            PaymentInfo result = paymentFacade.processCallback("txn-001", "SUCCESS", null);

            // then
            assertThat(result.status()).isEqualTo("COMPLETED");
            verify(order).completePayment();
        }

        @Test
        @DisplayName("실패 콜백 수신 시 결제 실패 및 보상 처리한다")
        void failedCallback() {
            // given
            Payment payment = Payment.create(MEMBER_ID, ORDER_ID, "SAMSUNG", "1234-5678-9012-3456", 50000L);
            payment.startExecution();
            payment.markProcessing("txn-001");
            given(paymentService.getPaymentByTransactionKeyForUpdate("txn-001")).willReturn(payment);

            Order order = mock(Order.class);
            given(orderService.getOrder(ORDER_ID)).willReturn(order);
            given(orderService.getOrderItems(ORDER_ID)).willReturn(List.of());
            given(order.getMemberCouponId()).willReturn(null);

            // when
            PaymentInfo result = paymentFacade.processCallback("txn-001", "FAILED", "한도 초과");

            // then
            assertThat(result.status()).isEqualTo("FAILED");
            verify(order).failPayment();
        }

        @Test
        @DisplayName("이미 완료된 결제의 콜백은 무시한다 (멱등)")
        void duplicateCallbackIgnored() {
            // given
            Payment payment = Payment.create(MEMBER_ID, ORDER_ID, "SAMSUNG", "1234-5678-9012-3456", 50000L);
            payment.startExecution();
            payment.markProcessing("txn-001");
            payment.complete();
            given(paymentService.getPaymentByTransactionKeyForUpdate("txn-001")).willReturn(payment);

            // when
            PaymentInfo result = paymentFacade.processCallback("txn-001", "SUCCESS", null);

            // then
            assertThat(result.status()).isEqualTo("COMPLETED");
            verify(orderService, never()).getOrder(anyLong());
        }
    }

    @Nested
    @DisplayName("timeoutPayment")
    class TimeoutPayment {

        @Test
        @DisplayName("PROCESSING 상태의 결제를 타임아웃 처리하고 재고를 복원한다")
        void success() {
            // given
            Payment payment = Payment.create(MEMBER_ID, ORDER_ID, "SAMSUNG", "1234-5678-9012-3456", 50000L);
            payment.startExecution();
            payment.markProcessing("txn-001");
            given(paymentService.getPaymentForUpdate(PAYMENT_ID)).willReturn(payment);

            Order order = mock(Order.class);
            given(orderService.getOrder(ORDER_ID)).willReturn(order);
            given(orderService.getOrderItems(ORDER_ID)).willReturn(List.of());
            given(order.getMemberCouponId()).willReturn(null);

            // when
            paymentFacade.timeoutPayment(PAYMENT_ID);

            // then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.TIMEOUT);
            assertThat(payment.getFailReason()).isEqualTo("PG 응답 타임아웃");
            verify(order).failPayment();
        }
    }

    @Nested
    @DisplayName("processCallback — 늦은 콜백 방어")
    class LateCallbackDefense {

        @Test
        @DisplayName("TIMEOUT 상태에서 늦은 SUCCESS 콜백이 오면 Payment는 COMPLETED이지만 Order는 변경하지 않는다")
        void lateSuccessCallbackAfterTimeout() {
            // given
            Payment payment = Payment.create(MEMBER_ID, ORDER_ID, "SAMSUNG", "1234-5678-9012-3456", 50000L);
            payment.startExecution();
            payment.markProcessing("txn-001");
            payment.timeout();
            given(paymentService.getPaymentByTransactionKeyForUpdate("txn-001")).willReturn(payment);

            Order order = mock(Order.class);
            given(order.getStatus()).willReturn(OrderStatus.PAYMENT_FAILED);
            given(orderService.getOrder(ORDER_ID)).willReturn(order);

            // when
            PaymentInfo result = paymentFacade.processCallback("txn-001", "SUCCESS", null);

            // then
            assertThat(result.status()).isEqualTo("COMPLETED");
            verify(order, never()).completePayment();
        }

        @Test
        @DisplayName("TIMEOUT 상태에서 콜백 SUCCESS가 오고 Order가 아직 PENDING_PAYMENT이면 정상 완료한다")
        void lateSuccessCallbackOrderStillPending() {
            // given
            Payment payment = Payment.create(MEMBER_ID, ORDER_ID, "SAMSUNG", "1234-5678-9012-3456", 50000L);
            payment.startExecution();
            payment.markProcessing("txn-001");
            payment.timeout();
            given(paymentService.getPaymentByTransactionKeyForUpdate("txn-001")).willReturn(payment);

            Order order = mock(Order.class);
            given(order.getStatus()).willReturn(OrderStatus.PENDING_PAYMENT);
            given(orderService.getOrder(ORDER_ID)).willReturn(order);

            // when
            PaymentInfo result = paymentFacade.processCallback("txn-001", "SUCCESS", null);

            // then
            assertThat(result.status()).isEqualTo("COMPLETED");
            verify(order).completePayment();
        }
    }

    @Nested
    @DisplayName("getPayment")
    class GetPayment {

        @Test
        @DisplayName("본인의 결제 정보를 조회한다")
        void success() {
            // given
            stubMember();
            Payment payment = Payment.create(MEMBER_ID, ORDER_ID, "SAMSUNG", "1234-5678-9012-3456", 50000L);
            given(paymentService.getPayment(PAYMENT_ID)).willReturn(payment);

            // when
            PaymentInfo result = paymentFacade.getPayment(LOGIN_ID, PAYMENT_ID);

            // then
            assertThat(result.memberId()).isEqualTo(MEMBER_ID);
        }

        @Test
        @DisplayName("다른 사람의 결제는 조회할 수 없다")
        void failWhenNotOwner() {
            // given
            stubMember();
            Payment payment = Payment.create(999L, ORDER_ID, "SAMSUNG", "1234-5678-9012-3456", 50000L);
            given(paymentService.getPayment(PAYMENT_ID)).willReturn(payment);

            // when & then
            assertThatThrownBy(() -> paymentFacade.getPayment(LOGIN_ID, PAYMENT_ID))
                .isInstanceOf(CoreException.class);
        }
    }
}

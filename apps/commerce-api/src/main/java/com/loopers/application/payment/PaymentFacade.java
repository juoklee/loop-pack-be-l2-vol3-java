package com.loopers.application.payment;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayResponse;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private final MemberService memberService;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final ProductService productService;
    private final CouponService couponService;
    private final PaymentGateway paymentGateway;

    @Lazy @Autowired
    private PaymentFacade self;

    @Transactional
    public PaymentInfo createPayment(String loginId, Long orderId, String cardType, String cardNo) {
        Long memberId = getMemberId(loginId);
        Order order = orderService.getOrderForMember(orderId, memberId);

        if (order.getStatus() != com.loopers.domain.order.OrderStatus.PENDING_PAYMENT) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 대기 상태의 주문만 결제할 수 있습니다.");
        }

        Payment payment = paymentService.createPayment(
            memberId, orderId, cardType, cardNo, order.getTotalAmount()
        );

        return PaymentInfo.from(payment);
    }

    public PaymentInfo executePayment(String loginId, Long paymentId) {
        Long memberId = getMemberId(loginId);
        Payment payment = getPaymentWithOwnerCheck(paymentId, memberId);

        if (payment.getStatus() != com.loopers.domain.payment.PaymentStatus.REQUESTED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "요청 상태의 결제만 실행할 수 있습니다.");
        }

        // PG 호출 (트랜잭션 밖)
        PaymentGatewayResponse pgResponse = paymentGateway.requestPayment(
            memberId,
            formatOrderIdForPg(payment.getOrderId()),
            payment.getCardType(),
            payment.getCardNo(),
            payment.getAmount()
        );

        // PG 응답에 따른 상태 갱신 (별도 트랜잭션 — self-injection으로 프록시 통해 호출)
        return self.processPaymentResponse(paymentId, pgResponse);
    }

    @Transactional
    public PaymentInfo processPaymentResponse(Long paymentId, PaymentGatewayResponse pgResponse) {
        Payment payment = paymentService.getPayment(paymentId);

        if (pgResponse == null) {
            // CircuitBreaker fallback: null 반환 → 실패 처리
            payment.fail("PG 서비스 일시 장애");
            compensateOrder(payment);
            return PaymentInfo.from(payment);
        }

        if (pgResponse.isPending()) {
            payment.markProcessing(pgResponse.transactionKey());
        } else if (pgResponse.isSuccess()) {
            payment.markProcessing(pgResponse.transactionKey());
            payment.complete();
            completeOrder(payment);
        } else if (pgResponse.isFailed()) {
            payment.markProcessing(pgResponse.transactionKey());
            payment.fail(pgResponse.reason());
            compensateOrder(payment);
        }

        return PaymentInfo.from(payment);
    }

    @Transactional
    public PaymentInfo processCallback(String transactionKey, String status, String reason) {
        Payment payment = paymentService.getPaymentByTransactionKey(transactionKey);

        if (payment.getStatus().isTerminal()) {
            log.info("이미 처리된 결제 콜백 무시. transactionKey={}, status={}",
                transactionKey, payment.getStatus());
            return PaymentInfo.from(payment);
        }

        if ("SUCCESS".equals(status)) {
            payment.complete();
            completeOrder(payment);
        } else if ("FAILED".equals(status)) {
            payment.fail(reason);
            compensateOrder(payment);
        }

        return PaymentInfo.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentInfo getPayment(String loginId, Long paymentId) {
        Long memberId = getMemberId(loginId);
        Payment payment = getPaymentWithOwnerCheck(paymentId, memberId);
        return PaymentInfo.from(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentInfo> getPaymentsByOrderId(String loginId, Long orderId) {
        Long memberId = getMemberId(loginId);
        orderService.getOrderForMember(orderId, memberId);
        return paymentService.getPaymentsByOrderId(orderId).stream()
            .map(PaymentInfo::from)
            .toList();
    }

    private void completeOrder(Payment payment) {
        Order order = orderService.getOrder(payment.getOrderId());
        order.completePayment();
    }

    private void compensateOrder(Payment payment) {
        Order order = orderService.getOrder(payment.getOrderId());
        order.failPayment();

        // 재고 복원 (productId 순 정렬 — 데드락 방지)
        List<OrderItem> items = orderService.getOrderItems(payment.getOrderId());
        List<OrderItem> sortedItems = items.stream()
            .sorted(Comparator.comparingLong(OrderItem::getProductId))
            .toList();

        for (OrderItem item : sortedItems) {
            Product product = productService.getProductForUpdate(item.getProductId());
            product.increaseStock(item.getQuantity());
        }

        // 쿠폰 복원
        if (order.getMemberCouponId() != null) {
            couponService.restoreCoupon(order.getMemberCouponId());
        }
    }

    private Payment getPaymentWithOwnerCheck(Long paymentId, Long memberId) {
        Payment payment = paymentService.getPayment(paymentId);
        if (!payment.getMemberId().equals(memberId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다.");
        }
        return payment;
    }

    private Long getMemberId(String loginId) {
        Member member = memberService.getMemberByLoginId(loginId);
        return member.getId();
    }

    static String formatOrderIdForPg(Long orderId) {
        return String.format("%06d", orderId);
    }
}

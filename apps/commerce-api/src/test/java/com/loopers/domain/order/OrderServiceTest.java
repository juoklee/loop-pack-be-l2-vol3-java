package com.loopers.domain.order;

import com.loopers.domain.PageResult;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.loopers.domain.PageResult;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderServiceTest {

    private OrderService orderService;
    private FakeOrderRepository fakeOrderRepository;
    private FakeOrderReader fakeOrderReader;
    private FakeOrderItemRepository fakeOrderItemRepository;
    private FakeOrderItemReader fakeOrderItemReader;

    @BeforeEach
    void setUp() {
        fakeOrderRepository = new FakeOrderRepository();
        fakeOrderReader = new FakeOrderReader();
        fakeOrderItemRepository = new FakeOrderItemRepository();
        fakeOrderItemReader = new FakeOrderItemReader();
        orderService = new OrderService(fakeOrderRepository, fakeOrderReader, fakeOrderItemRepository, fakeOrderItemReader);
    }

    @DisplayName("주문을 생성할 때, ")
    @Nested
    class CreateOrder {

        @DisplayName("정상적으로 주문이 저장된다.")
        @Test
        void savesOrder() {
            // Act
            Order order = orderService.createOrder(
                1L, "홍길동", "010-1234-5678", "12345", "서울시 강남구", null, 258000L
            );

            // Assert
            assertAll(
                () -> assertThat(order.getMemberId()).isEqualTo(1L),
                () -> assertThat(order.getTotalAmount()).isEqualTo(258000L),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT),
                () -> assertThat(fakeOrderRepository.getSavedOrders()).hasSize(1)
            );
        }
    }

    @DisplayName("주문 항목을 생성할 때, ")
    @Nested
    class CreateOrderItems {

        @DisplayName("정상적으로 주문 항목들이 저장된다.")
        @Test
        void savesOrderItems() {
            // Arrange
            List<OrderService.OrderItemCommand> commands = List.of(
                new OrderService.OrderItemCommand(10L, "에어맥스 90", 139000L, 1),
                new OrderService.OrderItemCommand(20L, "에어포스 1", 119000L, 2)
            );

            // Act
            List<OrderItem> items = orderService.createOrderItems(1L, commands);

            // Assert
            assertAll(
                () -> assertThat(items).hasSize(2),
                () -> assertThat(items.get(0).getProductName()).isEqualTo("에어맥스 90"),
                () -> assertThat(items.get(1).getQuantity()).isEqualTo(2)
            );
        }
    }

    @DisplayName("주문을 조회할 때, ")
    @Nested
    class GetOrder {

        @DisplayName("존재하는 주문이면, 주문을 반환한다.")
        @Test
        void returnsOrder_whenExists() {
            // Arrange
            Order order = Order.create(1L, "홍길동", "010-1234-5678", "12345", "주소", null, 100000L);
            fakeOrderReader.addOrder(order);

            // Act
            Order found = orderService.getOrder(order.getId());

            // Assert
            assertThat(found.getMemberId()).isEqualTo(1L);
        }

        @DisplayName("존재하지 않는 주문이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotExists() {
            CoreException exception = assertThrows(CoreException.class, () ->
                orderService.getOrder(999L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("회원 주문을 조회할 때, ")
    @Nested
    class GetOrderForMember {

        @DisplayName("소유권이 일치하면, 주문을 반환한다.")
        @Test
        void returnsOrder_whenOwnerMatches() {
            // Arrange
            Order order = Order.create(1L, "홍길동", "010-1234-5678", "12345", "주소", null, 100000L);
            fakeOrderReader.addOrder(order);

            // Act
            Order found = orderService.getOrderForMember(order.getId(), 1L);

            // Assert
            assertThat(found.getMemberId()).isEqualTo(1L);
        }

        @DisplayName("소유권이 불일치하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOwnerMismatch() {
            // Arrange
            Order order = Order.create(1L, "홍길동", "010-1234-5678", "12345", "주소", null, 100000L);
            fakeOrderReader.addOrder(order);

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                orderService.getOrderForMember(order.getId(), 999L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("주문 항목을 병합할 때, ")
    @Nested
    class MergeOrderItems {

        @DisplayName("중복 상품은 수량이 합산된다.")
        @Test
        void mergesDuplicateProducts() {
            // Arrange
            List<OrderService.OrderItemRequest> requests = List.of(
                new OrderService.OrderItemRequest(1L, 2),
                new OrderService.OrderItemRequest(2L, 1),
                new OrderService.OrderItemRequest(1L, 3)
            );

            // Act
            Map<Long, Integer> merged = orderService.mergeOrderItems(requests);

            // Assert
            assertAll(
                () -> assertThat(merged).hasSize(2),
                () -> assertThat(merged.get(1L)).isEqualTo(5),
                () -> assertThat(merged.get(2L)).isEqualTo(1)
            );
        }

        @DisplayName("항목이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmpty() {
            CoreException exception = assertThrows(CoreException.class, () ->
                orderService.mergeOrderItems(List.of())
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("항목이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNull() {
            CoreException exception = assertThrows(CoreException.class, () ->
                orderService.mergeOrderItems(null)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수량이 0 이하이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsZeroOrNegative() {
            List<OrderService.OrderItemRequest> requests = List.of(
                new OrderService.OrderItemRequest(1L, 0)
            );

            CoreException exception = assertThrows(CoreException.class, () ->
                orderService.mergeOrderItems(requests)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("주문을 취소할 때, ")
    @Nested
    class CancelOrder {

        @DisplayName("정상적으로 취소되고, 주문 항목을 반환한다.")
        @Test
        void cancelsOrder_andReturnsItems() {
            // Arrange
            Order order = Order.create(1L, "홍길동", "010-1234-5678", "12345", "주소", null, 100000L);
            fakeOrderReader.addOrder(order);
            OrderItem item = OrderItem.create(order.getId(), 10L, "에어맥스", 100000L, 1);
            fakeOrderItemReader.addItem(item);

            // Act
            OrderService.CancelOrderResult result = orderService.cancelOrder(order.getId(), 1L);

            // Assert
            assertAll(
                () -> assertThat(result.order().getStatus()).isEqualTo(OrderStatus.CANCELLED),
                () -> assertThat(result.items()).hasSize(1)
            );
        }

        @DisplayName("이미 취소된 주문이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenAlreadyCancelled() {
            // Arrange
            Order order = Order.create(1L, "홍길동", "010-1234-5678", "12345", "주소", null, 100000L);
            order.cancel();
            fakeOrderReader.addOrder(order);

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                orderService.cancelOrder(order.getId(), 1L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("소유권이 불일치하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOwnerMismatch() {
            // Arrange
            Order order = Order.create(1L, "홍길동", "010-1234-5678", "12345", "주소", null, 100000L);
            fakeOrderReader.addOrder(order);

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                orderService.cancelOrder(order.getId(), 999L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("내 주문 목록을 조회할 때, ")
    @Nested
    class GetMyOrders {

        @DisplayName("주문이 있으면, 페이징된 결과를 반환한다.")
        @Test
        void returnsPagedResult_whenOrdersExist() {
            // Arrange
            fakeOrderReader.addOrder(Order.create(1L, "홍길동", "010-1234-5678", "12345", "주소", null, 100000L));
            fakeOrderReader.addOrder(Order.create(1L, "홍길동", "010-1234-5678", "12345", "주소", null, 200000L));
            fakeOrderReader.addOrder(Order.create(2L, "김철수", "010-9999-9999", "54321", "다른주소", null, 50000L));

            // Act
            PageResult<Order> result = orderService.getMyOrders(1L, null, null, 0, 20);

            // Assert
            assertThat(result.content()).hasSize(2);
            assertThat(result.totalElements()).isEqualTo(2);
        }

        @DisplayName("주문이 없으면, 빈 결과를 반환한다.")
        @Test
        void returnsEmptyResult_whenNoOrders() {
            // Act
            PageResult<Order> result = orderService.getMyOrders(1L, null, null, 0, 20);

            // Assert
            assertThat(result.content()).isEmpty();
        }
    }

    // --- Fake 구현체 ---

    static class FakeOrderRepository implements OrderRepository {
        private final List<Order> savedOrders = new ArrayList<>();

        List<Order> getSavedOrders() { return savedOrders; }

        @Override
        public Order save(Order order) {
            savedOrders.add(order);
            return order;
        }
    }

    static class FakeOrderReader implements OrderReader {
        private final List<Order> orders = new ArrayList<>();

        void addOrder(Order order) { orders.add(order); }

        @Override
        public Optional<Order> findById(Long id) {
            return orders.stream()
                .filter(o -> o.getId().equals(id))
                .findFirst();
        }

        @Override
        public Optional<Order> findByIdAndMemberId(Long id, Long memberId) {
            return orders.stream()
                .filter(o -> o.getId().equals(id) && o.getMemberId().equals(memberId))
                .findFirst();
        }

        @Override
        public Optional<Order> findByIdAndMemberIdForUpdate(Long id, Long memberId) {
            return findByIdAndMemberId(id, memberId);
        }

        @Override
        public PageResult<Order> findAllByMemberId(Long memberId, LocalDate startAt, LocalDate endAt, int page, int size) {
            List<Order> filtered = orders.stream()
                .filter(o -> o.getMemberId().equals(memberId))
                .toList();
            return new PageResult<>(filtered, filtered.size(), 1, page, size);
        }

        @Override
        public PageResult<Order> findAll(Long memberId, int page, int size) {
            List<Order> filtered = memberId != null
                ? orders.stream().filter(o -> o.getMemberId().equals(memberId)).toList()
                : orders;
            return new PageResult<>(filtered, filtered.size(), 1, page, size);
        }
    }

    static class FakeOrderItemRepository implements OrderItemRepository {
        private final List<OrderItem> savedItems = new ArrayList<>();

        @Override
        public OrderItem save(OrderItem orderItem) {
            savedItems.add(orderItem);
            return orderItem;
        }

        @Override
        public List<OrderItem> saveAll(List<OrderItem> orderItems) {
            savedItems.addAll(orderItems);
            return orderItems;
        }
    }

    static class FakeOrderItemReader implements OrderItemReader {
        private final List<OrderItem> items = new ArrayList<>();

        void addItem(OrderItem item) { items.add(item); }

        @Override
        public List<OrderItem> findAllByOrderId(Long orderId) {
            return items.stream().filter(i -> i.getOrderId().equals(orderId)).toList();
        }

        @Override
        public List<OrderItem> findAllByOrderIds(List<Long> orderIds) {
            return items.stream().filter(i -> orderIds.contains(i.getOrderId())).toList();
        }
    }
}

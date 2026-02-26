package com.loopers.domain.order;

import com.loopers.domain.PageResult;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED),
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
        public Page<Order> findAllByMemberId(Long memberId, LocalDate startAt, LocalDate endAt, Pageable pageable) {
            List<Order> filtered = orders.stream()
                .filter(o -> o.getMemberId().equals(memberId))
                .toList();
            return new PageImpl<>(filtered, pageable, filtered.size());
        }

        @Override
        public Page<Order> findAll(Long memberId, Pageable pageable) {
            List<Order> filtered = memberId != null
                ? orders.stream().filter(o -> o.getMemberId().equals(memberId)).toList()
                : orders;
            return new PageImpl<>(filtered, pageable, filtered.size());
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

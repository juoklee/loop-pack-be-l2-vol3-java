package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.MetricsAggregator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

    @InjectMocks
    private OrderEventConsumer orderEventConsumer;

    @Mock
    private MetricsAggregator metricsAggregator;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private Acknowledgment ack;

    @Captor
    private ArgumentCaptor<List<MetricsAggregator.OrderItemInfo>> itemsCaptor;

    @Test
    @DisplayName("ORDER_CREATED 이벤트를 처리하여 상품별 집계한다")
    void handleOrderCreated() {
        // given
        String payload = """
            {
                "orderId": 10,
                "memberId": 1,
                "totalAmount": 50000,
                "items": [
                    {"productId": 1, "productName": "상품A", "price": 10000, "quantity": 2},
                    {"productId": 2, "productName": "상품B", "price": 30000, "quantity": 1}
                ]
            }
            """;
        ConsumerRecord<Object, Object> record = createRecord("order-events", payload, "100");

        // when
        orderEventConsumer.consume(record, ack);

        // then
        verify(metricsAggregator).handleOrderCreated(eq("100"), itemsCaptor.capture());
        List<MetricsAggregator.OrderItemInfo> items = itemsCaptor.getValue();
        assertThat(items).hasSize(2);
        assertThat(items.get(0).productId()).isEqualTo(1L);
        assertThat(items.get(0).price()).isEqualTo(10000L);
        assertThat(items.get(0).quantity()).isEqualTo(2);
        assertThat(items.get(1).productId()).isEqualTo(2L);
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("PAYMENT_COMPLETED 이벤트를 처리한다")
    void handlePaymentCompleted() {
        // given
        String payload = """
            {
                "paymentId": 100,
                "orderId": 10,
                "memberId": 1,
                "amount": 50000
            }
            """;
        ConsumerRecord<Object, Object> record = createRecord("order-events", payload, "200");

        // when
        orderEventConsumer.consume(record, ack);

        // then
        verify(metricsAggregator).handlePaymentCompleted("200", 10L);
        verify(ack).acknowledge();
    }

    private ConsumerRecord<Object, Object> createRecord(String topic, String jsonPayload, String eventId) {
        byte[] value;
        try {
            value = objectMapper.writeValueAsBytes(jsonPayload);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>(topic, 0, 0, "key", value);
        record.headers().add(new RecordHeader("eventId", eventId.getBytes(StandardCharsets.UTF_8)));
        return record;
    }
}

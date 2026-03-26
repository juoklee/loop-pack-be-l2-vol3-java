package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.MetricsAggregator;
import com.loopers.confg.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class OrderEventConsumer {

    private final MetricsAggregator metricsAggregator;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "order-events",
        groupId = "streamer-order-group",
        containerFactory = KafkaConfig.SINGLE_LISTENER
    )
    public void consume(ConsumerRecord<Object, Object> record, Acknowledgment ack) {
        String eventId = extractEventId(record);

        try {
            String jsonPayload = unwrapPayload(record.value());
            JsonNode node = objectMapper.readTree(jsonPayload);

            if (node.has("items")) {
                handleOrderCreated(eventId, node);
            } else if (node.has("paymentId")) {
                handlePaymentCompleted(eventId, node);
            } else {
                log.warn("알 수 없는 order-events 메시지: eventId={}", eventId);
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("order-events 처리 실패. eventId={}, error={}", eventId, e.getMessage(), e);
            ack.acknowledge();
        }
    }

    private void handleOrderCreated(String eventId, JsonNode node) {
        JsonNode itemsNode = node.get("items");
        List<MetricsAggregator.OrderItemInfo> items = new ArrayList<>();

        for (JsonNode item : itemsNode) {
            items.add(new MetricsAggregator.OrderItemInfo(
                item.get("productId").asLong(),
                item.get("price").asLong(),
                item.get("quantity").asInt()
            ));
        }

        metricsAggregator.handleOrderCreated(eventId, items);
    }

    private void handlePaymentCompleted(String eventId, JsonNode node) {
        Long orderId = node.get("orderId").asLong();
        metricsAggregator.handlePaymentCompleted(eventId, orderId);
    }

    private String extractEventId(ConsumerRecord<Object, Object> record) {
        Header header = record.headers().lastHeader("eventId");
        if (header != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return "unknown-" + record.topic() + "-" + record.partition() + "-" + record.offset();
    }

    private String unwrapPayload(Object value) throws Exception {
        if (value instanceof byte[] bytes) {
            return objectMapper.readValue(bytes, String.class);
        }
        return objectMapper.readValue(value.toString(), String.class);
    }
}

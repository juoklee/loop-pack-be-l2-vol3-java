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

@Slf4j
@RequiredArgsConstructor
@Component
public class CatalogEventConsumer {

    private final MetricsAggregator metricsAggregator;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "catalog-events",
        groupId = "streamer-catalog-group",
        containerFactory = KafkaConfig.SINGLE_LISTENER
    )
    public void consume(ConsumerRecord<Object, Object> record, Acknowledgment ack) {
        String eventId = extractEventId(record);

        try {
            String jsonPayload = unwrapPayload(record.value());
            JsonNode node = objectMapper.readTree(jsonPayload);

            String targetType = node.get("targetType").asText();
            if (!"PRODUCT".equals(targetType)) {
                log.debug("PRODUCT 외 타입 무시: targetType={}", targetType);
                ack.acknowledge();
                return;
            }

            Long productId = node.get("targetId").asLong();
            boolean liked = node.get("liked").asBoolean();

            metricsAggregator.handleLikeToggled(eventId, productId, liked);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("catalog-events 처리 실패. eventId={}, error={}", eventId, e.getMessage(), e);
            ack.acknowledge();
        }
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

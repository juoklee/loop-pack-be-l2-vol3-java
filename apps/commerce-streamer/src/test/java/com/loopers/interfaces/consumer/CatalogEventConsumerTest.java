package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.MetricsAggregator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CatalogEventConsumerTest {

    @InjectMocks
    private CatalogEventConsumer catalogEventConsumer;

    @Mock
    private MetricsAggregator metricsAggregator;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private Acknowledgment ack;

    @Test
    @DisplayName("PRODUCT 좋아요 이벤트를 처리한다")
    void handleProductLikeToggled() {
        // given
        String payload = "{\"memberId\":1,\"targetType\":\"PRODUCT\",\"targetId\":5,\"liked\":true}";
        ConsumerRecord<Object, Object> record = createRecord("catalog-events", payload, "42");

        // when
        catalogEventConsumer.consume(record, ack);

        // then
        verify(metricsAggregator).handleLikeToggled("42", 5L, true);
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("PRODUCT 좋아요 취소 이벤트를 처리한다")
    void handleProductLikeUntoggled() {
        // given
        String payload = "{\"memberId\":1,\"targetType\":\"PRODUCT\",\"targetId\":5,\"liked\":false}";
        ConsumerRecord<Object, Object> record = createRecord("catalog-events", payload, "43");

        // when
        catalogEventConsumer.consume(record, ack);

        // then
        verify(metricsAggregator).handleLikeToggled("43", 5L, false);
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("BRAND 타입 이벤트는 무시한다")
    void ignoreBrandLike() {
        // given
        String payload = "{\"memberId\":1,\"targetType\":\"BRAND\",\"targetId\":3,\"liked\":true}";
        ConsumerRecord<Object, Object> record = createRecord("catalog-events", payload, "44");

        // when
        catalogEventConsumer.consume(record, ack);

        // then
        verify(metricsAggregator, never()).handleLikeToggled(eq("44"), eq(3L), eq(true));
        verify(ack).acknowledge();
    }

    private ConsumerRecord<Object, Object> createRecord(String topic, String jsonPayload, String eventId) {
        // Producer의 JsonSerializer가 String을 JSON 인코딩하므로 동일하게 시뮬레이션
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

package com.loopers.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.outbox.OutboxEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class OutboxEventPublisherImpl implements OutboxEventPublisher {

    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(String aggregateType, Long aggregateId, String eventType,
                        String topic, String partitionKey, Object payload) {
        String json = serialize(payload);
        OutboxEvent outboxEvent = new OutboxEvent(
            aggregateType, aggregateId, eventType, topic, partitionKey, json
        );
        outboxEventJpaRepository.save(outboxEvent);
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Outbox 이벤트 직렬화 실패", e);
        }
    }
}

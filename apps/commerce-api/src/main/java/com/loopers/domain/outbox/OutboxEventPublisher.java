package com.loopers.domain.outbox;

public interface OutboxEventPublisher {

    void publish(String aggregateType, Long aggregateId, String eventType,
                 String topic, String partitionKey, Object payload);
}

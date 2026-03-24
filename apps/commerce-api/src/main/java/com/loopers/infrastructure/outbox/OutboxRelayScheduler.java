package com.loopers.infrastructure.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class OutboxRelayScheduler {

    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void relay() {
        List<OutboxEvent> events = outboxEventJpaRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc();

        for (OutboxEvent event : events) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getPartitionKey(), event.getPayload())
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Kafka 발행 실패. eventId={}, topic={}, error={}",
                                event.getId(), event.getTopic(), ex.getMessage());
                        }
                    });
                event.markPublished();
            } catch (Exception e) {
                log.error("Outbox relay 실패. eventId={}, error={}", event.getId(), e.getMessage());
            }
        }

        if (!events.isEmpty()) {
            log.info("Outbox relay 완료: {}건", events.size());
        }
    }
}

package com.loopers.infrastructure.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
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
                ProducerRecord<Object, Object> record = new ProducerRecord<>(
                    event.getTopic(), null, event.getPartitionKey(), event.getPayload()
                );
                record.headers().add(new RecordHeader(
                    "eventId", String.valueOf(event.getId()).getBytes(StandardCharsets.UTF_8)
                ));

                kafkaTemplate.send(record).get();
                event.markPublished();
            } catch (Exception e) {
                log.error("Outbox relay 실패. eventId={}, topic={}, error={}",
                    event.getId(), event.getTopic(), e.getMessage());
                break;
            }
        }

        if (!events.isEmpty()) {
            log.info("Outbox relay 완료: {}건", events.size());
        }
    }
}

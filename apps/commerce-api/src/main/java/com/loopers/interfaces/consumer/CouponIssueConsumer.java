package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.CouponIssueFacade;
import com.loopers.confg.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class CouponIssueConsumer {

    private final CouponIssueFacade couponIssueFacade;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "coupon-issue-requests",
        groupId = "coupon-issue-group",
        containerFactory = KafkaConfig.SINGLE_LISTENER
    )
    public void consume(ConsumerRecord<Object, Object> record, Acknowledgment ack) {
        try {
            String jsonPayload = unwrapPayload(record.value());
            JsonNode node = objectMapper.readTree(jsonPayload);
            String requestId = node.get("requestId").asText();

            couponIssueFacade.processIssuance(requestId);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("coupon-issue-requests 처리 실패. error={}", e.getMessage(), e);
            ack.acknowledge();
        }
    }

    private String unwrapPayload(Object value) throws Exception {
        if (value instanceof byte[] bytes) {
            return objectMapper.readValue(bytes, String.class);
        }
        return objectMapper.readValue(value.toString(), String.class);
    }
}

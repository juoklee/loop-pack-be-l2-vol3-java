package com.loopers.domain.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "event_handled")
public class EventHandled {

    @Id
    @Column(name = "event_id", length = 100)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "handled_at", nullable = false)
    private ZonedDateTime handledAt;

    public static EventHandled of(String eventId, String eventType) {
        EventHandled entity = new EventHandled();
        entity.eventId = eventId;
        entity.eventType = eventType;
        entity.handledAt = ZonedDateTime.now();
        return entity;
    }
}

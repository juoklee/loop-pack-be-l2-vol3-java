package com.loopers.domain.idempotency;

public interface EventHandledRepository {

    boolean existsByEventId(String eventId);

    EventHandled save(EventHandled eventHandled);
}

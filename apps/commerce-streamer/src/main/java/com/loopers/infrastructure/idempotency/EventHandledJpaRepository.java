package com.loopers.infrastructure.idempotency;

import com.loopers.domain.idempotency.EventHandled;
import com.loopers.domain.idempotency.EventHandledRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventHandledJpaRepository extends JpaRepository<EventHandled, String>, EventHandledRepository {

    @Override
    boolean existsByEventId(String eventId);
}

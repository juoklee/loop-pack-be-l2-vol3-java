package com.loopers.infrastructure.order;

import com.loopers.domain.order.Order;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByIdAndDeletedAtIsNull(Long id);
    Optional<Order> findByIdAndMemberIdAndDeletedAtIsNull(Long id, Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT o FROM Order o WHERE o.id = :id AND o.memberId = :memberId AND o.deletedAt IS NULL")
    Optional<Order> findByIdAndMemberIdForUpdate(@Param("id") Long id, @Param("memberId") Long memberId);
}

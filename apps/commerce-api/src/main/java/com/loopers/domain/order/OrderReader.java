package com.loopers.domain.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Optional;

public interface OrderReader {
    Optional<Order> findById(Long id);
    Optional<Order> findByIdAndMemberId(Long id, Long memberId);
    Page<Order> findAllByMemberId(Long memberId, LocalDate startAt, LocalDate endAt, Pageable pageable);
    Page<Order> findAll(Long memberId, Pageable pageable);
}

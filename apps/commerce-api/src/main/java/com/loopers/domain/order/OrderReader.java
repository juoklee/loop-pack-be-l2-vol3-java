package com.loopers.domain.order;

import com.loopers.domain.PageResult;

import java.time.LocalDate;
import java.util.Optional;

public interface OrderReader {
    Optional<Order> findById(Long id);
    Optional<Order> findByIdAndMemberId(Long id, Long memberId);
    PageResult<Order> findAllByMemberId(Long memberId, LocalDate startAt, LocalDate endAt, int page, int size);
    PageResult<Order> findAll(Long memberId, int page, int size);
}

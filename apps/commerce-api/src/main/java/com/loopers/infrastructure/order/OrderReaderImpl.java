package com.loopers.infrastructure.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderReader;
import com.loopers.domain.order.QOrder;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class OrderReaderImpl implements OrderReader {

    private final OrderJpaRepository orderJpaRepository;
    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<Order> findById(Long id) {
        return orderJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Optional<Order> findByIdAndMemberId(Long id, Long memberId) {
        return orderJpaRepository.findByIdAndMemberIdAndDeletedAtIsNull(id, memberId);
    }

    @Override
    public Page<Order> findAllByMemberId(Long memberId, LocalDate startAt, LocalDate endAt, Pageable pageable) {
        QOrder order = QOrder.order;

        BooleanBuilder where = new BooleanBuilder();
        where.and(order.deletedAt.isNull());
        where.and(order.memberId.eq(memberId));

        if (startAt != null) {
            ZonedDateTime startDateTime = startAt.atStartOfDay(ZoneId.systemDefault());
            where.and(order.createdAt.goe(startDateTime));
        }
        if (endAt != null) {
            ZonedDateTime endDateTime = endAt.plusDays(1).atStartOfDay(ZoneId.systemDefault());
            where.and(order.createdAt.lt(endDateTime));
        }

        List<Order> content = queryFactory.selectFrom(order)
            .where(where)
            .orderBy(order.createdAt.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        Long total = queryFactory.select(order.count())
            .from(order)
            .where(where)
            .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public Page<Order> findAll(Long memberId, Pageable pageable) {
        QOrder order = QOrder.order;

        BooleanBuilder where = new BooleanBuilder();
        where.and(order.deletedAt.isNull());

        if (memberId != null) {
            where.and(order.memberId.eq(memberId));
        }

        List<Order> content = queryFactory.selectFrom(order)
            .where(where)
            .orderBy(order.createdAt.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        Long total = queryFactory.select(order.count())
            .from(order)
            .where(where)
            .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }
}

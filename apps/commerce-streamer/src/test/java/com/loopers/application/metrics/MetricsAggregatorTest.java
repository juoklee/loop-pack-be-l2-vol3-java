package com.loopers.application.metrics;

import com.loopers.domain.idempotency.EventHandled;
import com.loopers.domain.idempotency.EventHandledRepository;
import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductMetricsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MetricsAggregatorTest {

    @InjectMocks
    private MetricsAggregator metricsAggregator;

    @Mock
    private ProductMetricsRepository productMetricsRepository;

    @Mock
    private EventHandledRepository eventHandledRepository;

    @Nested
    @DisplayName("handleLikeToggled")
    class HandleLikeToggled {

        @Test
        @DisplayName("좋아요 시 likeCount를 증가시킨다")
        void incrementLikeCount() {
            // given
            given(eventHandledRepository.existsByEventId("1")).willReturn(false);
            ProductMetrics metrics = ProductMetrics.create(100L);
            given(productMetricsRepository.findByProductId(100L)).willReturn(Optional.of(metrics));

            // when
            metricsAggregator.handleLikeToggled("1", 100L, true);

            // then
            assertThat(metrics.getLikeCount()).isEqualTo(1);
            verify(eventHandledRepository).save(any(EventHandled.class));
        }

        @Test
        @DisplayName("좋아요 취소 시 likeCount를 감소시킨다")
        void decrementLikeCount() {
            // given
            given(eventHandledRepository.existsByEventId("2")).willReturn(false);
            ProductMetrics metrics = ProductMetrics.create(100L);
            metrics.incrementLikeCount();
            given(productMetricsRepository.findByProductId(100L)).willReturn(Optional.of(metrics));

            // when
            metricsAggregator.handleLikeToggled("2", 100L, false);

            // then
            assertThat(metrics.getLikeCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("likeCount가 0일 때 감소시켜도 0 이하로 내려가지 않는다")
        void decrementDoesNotGoBelowZero() {
            // given
            given(eventHandledRepository.existsByEventId("3")).willReturn(false);
            ProductMetrics metrics = ProductMetrics.create(100L);
            given(productMetricsRepository.findByProductId(100L)).willReturn(Optional.of(metrics));

            // when
            metricsAggregator.handleLikeToggled("3", 100L, false);

            // then
            assertThat(metrics.getLikeCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("중복 이벤트는 무시한다")
        void duplicateEventIgnored() {
            // given
            given(eventHandledRepository.existsByEventId("1")).willReturn(true);

            // when
            metricsAggregator.handleLikeToggled("1", 100L, true);

            // then
            verify(productMetricsRepository, never()).findByProductId(any());
        }

        @Test
        @DisplayName("ProductMetrics가 없으면 새로 생성한다")
        void createNewMetricsIfNotExists() {
            // given
            given(eventHandledRepository.existsByEventId("4")).willReturn(false);
            given(productMetricsRepository.findByProductId(100L)).willReturn(Optional.empty());
            ProductMetrics newMetrics = ProductMetrics.create(100L);
            given(productMetricsRepository.save(any(ProductMetrics.class))).willReturn(newMetrics);

            // when
            metricsAggregator.handleLikeToggled("4", 100L, true);

            // then
            assertThat(newMetrics.getLikeCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("handleOrderCreated")
    class HandleOrderCreated {

        @Test
        @DisplayName("주문 생성 시 상품별 orderCount와 salesAmount를 집계한다")
        void aggregateOrderMetrics() {
            // given
            given(eventHandledRepository.existsByEventId("10")).willReturn(false);
            ProductMetrics metrics1 = ProductMetrics.create(1L);
            ProductMetrics metrics2 = ProductMetrics.create(2L);
            given(productMetricsRepository.findByProductId(1L)).willReturn(Optional.of(metrics1));
            given(productMetricsRepository.findByProductId(2L)).willReturn(Optional.of(metrics2));

            List<MetricsAggregator.OrderItemInfo> items = List.of(
                new MetricsAggregator.OrderItemInfo(1L, 10000L, 2),
                new MetricsAggregator.OrderItemInfo(2L, 25000L, 1)
            );

            // when
            metricsAggregator.handleOrderCreated("10", items);

            // then
            assertThat(metrics1.getOrderCount()).isEqualTo(1);
            assertThat(metrics1.getSalesAmount()).isEqualTo(20000L);
            assertThat(metrics2.getOrderCount()).isEqualTo(1);
            assertThat(metrics2.getSalesAmount()).isEqualTo(25000L);
            verify(eventHandledRepository).save(any(EventHandled.class));
        }

        @Test
        @DisplayName("중복 이벤트는 무시한다")
        void duplicateEventIgnored() {
            // given
            given(eventHandledRepository.existsByEventId("10")).willReturn(true);

            // when
            metricsAggregator.handleOrderCreated("10", List.of());

            // then
            verify(productMetricsRepository, never()).findByProductId(any());
        }
    }
}

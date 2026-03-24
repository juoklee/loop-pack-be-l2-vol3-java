package com.loopers.application.event;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.event.LikeToggledEvent;
import com.loopers.domain.like.LikeTargetType;
import com.loopers.domain.product.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class LikeEventListenerTest {

    @InjectMocks
    private LikeEventListener likeEventListener;

    @Mock private ProductService productService;
    @Mock private BrandService brandService;

    @Nested
    @DisplayName("상품 좋아요 이벤트")
    class ProductLike {

        @Test
        @DisplayName("좋아요 시 상품 좋아요 수를 증가시킨다")
        void increaseLikeCount() {
            // given
            LikeToggledEvent event = new LikeToggledEvent(1L, LikeTargetType.PRODUCT, 10L, true);

            // when
            likeEventListener.handleLikeToggled(event);

            // then
            verify(productService).increaseLikeCount(10L);
            verifyNoInteractions(brandService);
        }

        @Test
        @DisplayName("좋아요 취소 시 상품 좋아요 수를 감소시킨다")
        void decreaseLikeCount() {
            // given
            LikeToggledEvent event = new LikeToggledEvent(1L, LikeTargetType.PRODUCT, 10L, false);

            // when
            likeEventListener.handleLikeToggled(event);

            // then
            verify(productService).decreaseLikeCount(10L);
            verifyNoInteractions(brandService);
        }
    }

    @Nested
    @DisplayName("브랜드 좋아요 이벤트")
    class BrandLike {

        @Test
        @DisplayName("좋아요 시 브랜드 좋아요 수를 증가시킨다")
        void increaseLikeCount() {
            // given
            LikeToggledEvent event = new LikeToggledEvent(1L, LikeTargetType.BRAND, 20L, true);

            // when
            likeEventListener.handleLikeToggled(event);

            // then
            verify(brandService).increaseLikeCount(20L);
            verifyNoInteractions(productService);
        }

        @Test
        @DisplayName("좋아요 취소 시 브랜드 좋아요 수를 감소시킨다")
        void decreaseLikeCount() {
            // given
            LikeToggledEvent event = new LikeToggledEvent(1L, LikeTargetType.BRAND, 20L, false);

            // when
            likeEventListener.handleLikeToggled(event);

            // then
            verify(brandService).decreaseLikeCount(20L);
            verifyNoInteractions(productService);
        }
    }
}

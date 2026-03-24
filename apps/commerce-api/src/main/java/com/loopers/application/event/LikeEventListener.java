package com.loopers.application.event;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.event.LikeToggledEvent;
import com.loopers.domain.like.LikeTargetType;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@RequiredArgsConstructor
@Component
public class LikeEventListener {

    private final ProductService productService;
    private final BrandService brandService;

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLikeToggled(LikeToggledEvent event) {
        log.info("[이벤트] 좋아요 토글 - memberId={}, targetType={}, targetId={}, liked={}",
            event.memberId(), event.targetType(), event.targetId(), event.liked());

        if (event.targetType() == LikeTargetType.PRODUCT) {
            if (event.liked()) {
                productService.increaseLikeCount(event.targetId());
            } else {
                productService.decreaseLikeCount(event.targetId());
            }
        } else if (event.targetType() == LikeTargetType.BRAND) {
            if (event.liked()) {
                brandService.increaseLikeCount(event.targetId());
            } else {
                brandService.decreaseLikeCount(event.targetId());
            }
        }
    }
}

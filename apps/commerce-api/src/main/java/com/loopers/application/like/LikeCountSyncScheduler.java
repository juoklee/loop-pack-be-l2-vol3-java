package com.loopers.application.like;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeCountProjection;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.like.LikeTargetType;
import com.loopers.domain.product.ProductService;
import com.loopers.support.cache.ProductCacheManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class LikeCountSyncScheduler {

    private final LikeService likeService;
    private final ProductService productService;
    private final BrandService brandService;
    private final ProductCacheManager productCacheManager;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(fixedRate = 300_000)
    public void syncLikeCounts() {
        syncProductLikeCounts();
        syncBrandLikeCounts();
    }

    private void syncProductLikeCounts() {
        transactionTemplate.executeWithoutResult(status -> {
            List<LikeCountProjection> counts = likeService.countAllLikes(LikeTargetType.PRODUCT);

            List<Long> targetIds = counts.stream().map(LikeCountProjection::targetId).toList();
            productService.resetLikeCountsNotIn(targetIds);

            for (LikeCountProjection projection : counts) {
                try {
                    productService.updateLikeCount(projection.targetId(), (int) projection.count());
                } catch (Exception e) {
                    log.error("상품 좋아요 수 동기화 실패 - targetId: {}, error: {}", projection.targetId(), e.getMessage());
                }
            }

            log.info("상품 좋아요 수 동기화 완료: {}건", counts.size());
        });

        productCacheManager.evictAllProductList();
        productCacheManager.evictAllProductDetail();
    }

    private void syncBrandLikeCounts() {
        transactionTemplate.executeWithoutResult(status -> {
            List<LikeCountProjection> counts = likeService.countAllLikes(LikeTargetType.BRAND);

            List<Long> targetIds = counts.stream().map(LikeCountProjection::targetId).toList();
            brandService.resetLikeCountsNotIn(targetIds);

            for (LikeCountProjection projection : counts) {
                try {
                    brandService.updateLikeCount(projection.targetId(), (int) projection.count());
                } catch (Exception e) {
                    log.error("브랜드 좋아요 수 동기화 실패 - targetId: {}, error: {}", projection.targetId(), e.getMessage());
                }
            }

            log.info("브랜드 좋아요 수 동기화 완료: {}건", counts.size());
        });
    }
}

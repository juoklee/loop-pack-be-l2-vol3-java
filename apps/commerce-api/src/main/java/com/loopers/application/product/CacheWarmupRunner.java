package com.loopers.application.product;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class CacheWarmupRunner {

    private static final int FIRST_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final String[] WARMUP_SORTS = {"LATEST", "LIKES_DESC"};

    private final ProductFacade productFacade;

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        log.info("[CacheWarmup] 상품 목록 캐시 워밍업 시작");
        for (String sort : WARMUP_SORTS) {
            try {
                productFacade.getProducts(null, null, sort, FIRST_PAGE, DEFAULT_SIZE);
                log.info("[CacheWarmup] 워밍업 완료 - sort={}", sort);
            } catch (Exception e) {
                log.warn("[CacheWarmup] 워밍업 실패 - sort={}, error={}", sort, e.getMessage());
            }
        }
        log.info("[CacheWarmup] 상품 목록 캐시 워밍업 종료");
    }
}

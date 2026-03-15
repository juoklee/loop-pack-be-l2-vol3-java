package com.loopers.support.cache;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductCacheManager {

    private final CacheManager cacheManager;

    public void evictProductDetail(Long productId) {
        org.springframework.cache.Cache cache = cacheManager.getCache("productDetail");
        if (cache != null) {
            cache.evict(productId);
        }
    }

    public void evictProductListByBrand(Long brandId) {
        Cache<Object, Object> nativeCache = getProductListNativeCache();
        if (nativeCache == null) return;

        String brandPrefix = (brandId != null ? brandId.toString() : "all") + ":";
        String allPrefix = "all:";

        nativeCache.asMap().keySet().removeIf(key -> {
            String keyStr = key.toString();
            return keyStr.startsWith(brandPrefix) || keyStr.startsWith(allPrefix);
        });
    }

    public void evictLikesSortFirstPage() {
        Cache<Object, Object> nativeCache = getProductListNativeCache();
        if (nativeCache == null) return;

        nativeCache.asMap().keySet().removeIf(key ->
            key.toString().contains(":LIKES_DESC:0:")
        );
    }

    public void evictAllProductList() {
        org.springframework.cache.Cache cache = cacheManager.getCache("productList");
        if (cache != null) {
            cache.clear();
        }
    }

    public void evictAllProductDetail() {
        org.springframework.cache.Cache cache = cacheManager.getCache("productDetail");
        if (cache != null) {
            cache.clear();
        }
    }

    private Cache<Object, Object> getProductListNativeCache() {
        org.springframework.cache.Cache cache = cacheManager.getCache("productList");
        if (cache == null) return null;
        return ((CaffeineCache) cache).getNativeCache();
    }
}

package com.loopers.application.like;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandReader;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeCountProjection;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.like.LikeTargetType;
import com.loopers.domain.PageResult;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductReader;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import com.loopers.support.cache.ProductCacheManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.*;

class LikeCountSyncSchedulerTest {

    private LikeService likeService;
    private ProductCacheManager productCacheManager;
    private ProductService productService;
    private BrandService brandService;
    private LikeCountSyncScheduler scheduler;

    private FakeProductRepository fakeProductRepository;
    private FakeBrandRepository fakeBrandRepository;

    @BeforeEach
    void setUp() {
        likeService = mock(LikeService.class);
        productCacheManager = mock(ProductCacheManager.class);

        FakeProductReader fakeProductReader = new FakeProductReader();
        fakeProductRepository = new FakeProductRepository();
        productService = new ProductService(fakeProductReader, fakeProductRepository);

        FakeBrandReader fakeBrandReader = new FakeBrandReader();
        fakeBrandRepository = new FakeBrandRepository();
        brandService = new BrandService(fakeBrandReader, fakeBrandRepository);

        scheduler = new LikeCountSyncScheduler(likeService, productService, brandService, productCacheManager);
    }

    @DisplayName("삭제된 엔티티가 좋아요 집계에 포함되어도, 에러를 로그로 남기고 나머지 항목을 정상 처리한다.")
    @Test
    void continuesProcessing_whenSomeEntitiesAreDeleted() {
        // Arrange - 상품: id 1(존재), id 999(삭제됨)
        fakeProductRepository.addExistingId(1L);

        when(likeService.countAllLikes(LikeTargetType.PRODUCT)).thenReturn(List.of(
                new LikeCountProjection(1L, 5),
                new LikeCountProjection(999L, 3)
        ));

        // 브랜드: id 1(존재), id 888(삭제됨)
        fakeBrandRepository.addExistingId(1L);

        when(likeService.countAllLikes(LikeTargetType.BRAND)).thenReturn(List.of(
                new LikeCountProjection(1L, 10),
                new LikeCountProjection(888L, 7)
        ));

        // Act & Assert - 예외 없이 완료
        assertThatNoException().isThrownBy(() -> scheduler.syncLikeCounts());

        // 캐시 무효화도 정상 수행됨
        verify(productCacheManager).evictAllProductList();
        verify(productCacheManager).evictAllProductDetail();
    }

    @DisplayName("모든 엔티티가 정상 존재하면, 예외 없이 동기화를 완료한다.")
    @Test
    void completesSuccessfully_whenAllEntitiesExist() {
        // Arrange
        fakeProductRepository.addExistingId(1L);
        fakeProductRepository.addExistingId(2L);

        when(likeService.countAllLikes(LikeTargetType.PRODUCT)).thenReturn(List.of(
                new LikeCountProjection(1L, 5),
                new LikeCountProjection(2L, 3)
        ));

        fakeBrandRepository.addExistingId(1L);

        when(likeService.countAllLikes(LikeTargetType.BRAND)).thenReturn(List.of(
                new LikeCountProjection(1L, 10)
        ));

        // Act & Assert
        assertThatNoException().isThrownBy(() -> scheduler.syncLikeCounts());
    }

    // Fake 구현체
    static class FakeProductReader implements ProductReader {
        @Override
        public Optional<Product> findById(Long id) { return Optional.empty(); }
        @Override
        public Optional<Product> findByIdForUpdate(Long id) { return Optional.empty(); }
        @Override
        public PageResult<Product> findAll(String keyword, Long brandId, ProductSortType sort, int page, int size) {
            return new PageResult<>(List.of(), 0, 0, page, size);
        }
        @Override
        public List<Product> findAllByIds(List<Long> ids) { return List.of(); }
        @Override
        public List<Product> findAllByBrandId(Long brandId) { return List.of(); }
    }

    static class FakeProductRepository implements ProductRepository {
        private final Map<Long, Boolean> existingIds = new HashMap<>();

        void addExistingId(Long id) { existingIds.put(id, true); }

        @Override
        public Product save(Product product) { return product; }
        @Override
        public int increaseLikeCount(Long id) { return existingIds.containsKey(id) ? 1 : 0; }
        @Override
        public int decreaseLikeCount(Long id) { return existingIds.containsKey(id) ? 1 : 0; }
        @Override
        public int updateLikeCount(Long id, int likeCount) { return existingIds.containsKey(id) ? 1 : 0; }
        @Override
        public int resetLikeCountsNotIn(List<Long> ids) { return 0; }
        @Override
        public int resetAllLikeCounts() { return 0; }
    }

    static class FakeBrandReader implements BrandReader {
        @Override
        public Optional<Brand> findById(Long id) { return Optional.empty(); }
        @Override
        public boolean existsById(Long id) { return false; }
        @Override
        public boolean existsByName(String name) { return false; }
        @Override
        public List<Brand> findAllByIds(List<Long> ids) { return List.of(); }
        @Override
        public PageResult<Brand> findAll(String keyword, int page, int size) {
            return new PageResult<>(List.of(), 0, 0, page, size);
        }
    }

    static class FakeBrandRepository implements BrandRepository {
        private final Map<Long, Boolean> existingIds = new HashMap<>();

        void addExistingId(Long id) { existingIds.put(id, true); }

        @Override
        public Brand save(Brand brand) { return brand; }
        @Override
        public int increaseLikeCount(Long id) { return existingIds.containsKey(id) ? 1 : 0; }
        @Override
        public int decreaseLikeCount(Long id) { return existingIds.containsKey(id) ? 1 : 0; }
        @Override
        public int updateLikeCount(Long id, int likeCount) { return existingIds.containsKey(id) ? 1 : 0; }
        @Override
        public int resetLikeCountsNotIn(List<Long> ids) { return 0; }
        @Override
        public int resetAllLikeCounts() { return 0; }
    }
}

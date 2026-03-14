package com.loopers.interfaces.api;

import com.loopers.interfaces.api.brand.BrandV1Dto;
import com.loopers.interfaces.api.product.ProductV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cache.CacheManager;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("상품 캐시 E2E 테스트")
class ProductCacheE2ETest {

    private static final Logger log = LoggerFactory.getLogger(ProductCacheE2ETest.class);
    private static final String PRODUCT_PUBLIC = "/api/v1/products";
    private static final String PRODUCT_ADMIN = "/api-admin/v1/products";
    private static final String BRAND_ADMIN = "/api-admin/v1/brands";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final CacheManager cacheManager;

    @Autowired
    public ProductCacheE2ETest(
        TestRestTemplate testRestTemplate,
        DatabaseCleanUp databaseCleanUp,
        CacheManager cacheManager
    ) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.cacheManager = cacheManager;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        cacheManager.getCache("productDetail").clear();
        cacheManager.getCache("productList").clear();
    }

    @DisplayName("상품 상세 캐시")
    @Nested
    class ProductDetailCache {

        @DisplayName("첫 번째 조회는 캐시 미스, 두 번째 조회는 캐시 히트한다.")
        @Test
        void cacheHitOnSecondCall() {
            // arrange
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);

            // act - 첫 번째 조회 (캐시 미스)
            assertThat(cacheManager.getCache("productDetail").get(productId)).isNull();

            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> first = getProduct(productId);
            assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

            // assert - 캐시에 저장되었는지 확인
            assertThat(cacheManager.getCache("productDetail").get(productId)).isNotNull();

            // act - 두 번째 조회 (캐시 히트)
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> second = getProduct(productId);

            // assert - 동일한 데이터 반환
            assertThat(second.getBody().data().product().name())
                .isEqualTo(first.getBody().data().product().name());
        }

        @DisplayName("상품 수정 시 캐시가 무효화된다.")
        @Test
        void cacheEvictedOnUpdate() {
            // arrange
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);
            getProduct(productId); // 캐시 저장
            assertThat(cacheManager.getCache("productDetail").get(productId)).isNotNull();

            // act - 상품 수정
            var updateRequest = new ProductV1Dto.UpdateRequest("에어맥스 95", "신상", 159000L, 3);
            testRestTemplate.exchange(
                PRODUCT_ADMIN + "/" + productId,
                HttpMethod.PUT,
                adminEntity(updateRequest),
                new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            // assert - 캐시 무효화
            assertThat(cacheManager.getCache("productDetail").get(productId)).isNull();

            // act - 다시 조회하면 새 데이터
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = getProduct(productId);
            assertThat(response.getBody().data().product().name()).isEqualTo("에어맥스 95");
        }

        @DisplayName("상품 삭제 시 캐시가 무효화된다.")
        @Test
        void cacheEvictedOnDelete() {
            // arrange
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);
            getProduct(productId); // 캐시 저장
            assertThat(cacheManager.getCache("productDetail").get(productId)).isNotNull();

            // act - 상품 삭제
            testRestTemplate.exchange(
                PRODUCT_ADMIN + "/" + productId,
                HttpMethod.DELETE,
                adminEntity(null),
                new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            // assert - 캐시 무효화
            assertThat(cacheManager.getCache("productDetail").get(productId)).isNull();
        }
    }

    @DisplayName("상품 목록 캐시")
    @Nested
    class ProductListCache {

        @DisplayName("keyword 없는 목록 조회는 캐시된다.")
        @Test
        void cachedWhenNoKeyword() {
            // arrange
            Long brandId = registerBrand("Nike", "Just Do It");
            registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);
            String cacheKey = brandId + ":LATEST:0:20";

            // act - 첫 번째 조회
            assertThat(cacheManager.getCache("productList").get(cacheKey)).isNull();
            getProducts(null, brandId, "LATEST", 0, 20);

            // assert - 캐시 저장
            assertThat(cacheManager.getCache("productList").get(cacheKey)).isNotNull();
        }

        @DisplayName("keyword 있는 목록 조회는 캐시되지 않는다.")
        @Test
        void notCachedWhenKeyword() {
            // arrange
            Long brandId = registerBrand("Nike", "Just Do It");
            registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);

            // act
            getProducts("에어맥스", brandId, "LATEST", 0, 20);

            // assert - 캐시에 저장 안 됨 (keyword 요청은 condition 불충족)
            String cacheKey = brandId + ":LATEST:0:20";
            assertThat(cacheManager.getCache("productList").get(cacheKey)).isNull();
        }
    }

    @DisplayName("캐시 성능 측정")
    @Nested
    class CachePerformanceMeasurement {

        private static final int WARM_UP_COUNT = 3;
        private static final int MEASURE_COUNT = 10;

        @DisplayName("상품 상세 조회: 캐시 미스(DB) vs 캐시 히트(Caffeine) 응답 시간 비교")
        @Test
        void productDetailCachePerformance() {
            // arrange
            Long brandId = registerBrand("Nike", "Just Do It");
            Long productId = registerProduct(brandId, "에어맥스 90", 139000L, 100, 5);

            // warm-up: JVM/커넥션풀 안정화
            for (int i = 0; i < WARM_UP_COUNT; i++) {
                getProduct(productId);
                cacheManager.getCache("productDetail").clear();
            }

            // --- 캐시 미스 측정 (매 요청마다 캐시 비우고 DB 조회) ---
            long[] cacheMissTimes = new long[MEASURE_COUNT];
            for (int i = 0; i < MEASURE_COUNT; i++) {
                cacheManager.getCache("productDetail").clear();
                long start = System.nanoTime();
                getProduct(productId);
                cacheMissTimes[i] = (System.nanoTime() - start) / 1_000_000;
            }

            // --- 캐시 히트 측정 (캐시에 저장된 상태에서 조회) ---
            getProduct(productId); // 캐시 저장
            long[] cacheHitTimes = new long[MEASURE_COUNT];
            for (int i = 0; i < MEASURE_COUNT; i++) {
                long start = System.nanoTime();
                getProduct(productId);
                cacheHitTimes[i] = (System.nanoTime() - start) / 1_000_000;
            }

            // 결과 계산
            long cacheMissAvg = average(cacheMissTimes);
            long cacheHitAvg = average(cacheHitTimes);
            double improvementRate = cacheMissAvg > 0
                ? ((double) (cacheMissAvg - cacheHitAvg) / cacheMissAvg) * 100 : 0;

            // 결과 출력
            log.info("========== 상품 상세 캐시 성능 측정 결과 ==========");
            log.info("캐시 미스 (DB 조회): 평균 {}ms  (각 회차: {})", cacheMissAvg, formatTimes(cacheMissTimes));
            log.info("캐시 히트 (Caffeine): 평균 {}ms  (각 회차: {})", cacheHitAvg, formatTimes(cacheHitTimes));
            log.info("개선율: {}% ({}ms → {}ms)", String.format("%.1f", improvementRate), cacheMissAvg, cacheHitAvg);
            log.info("================================================");

            // assert - 캐시 히트가 캐시 미스보다 빨라야 함
            assertThat(cacheHitAvg).isLessThan(cacheMissAvg);
        }

        @DisplayName("상품 목록 조회: 캐시 미스(DB) vs 캐시 히트(Caffeine) 응답 시간 비교")
        @Test
        void productListCachePerformance() {
            // arrange - 상품 여러 개 등록
            Long brandId = registerBrand("Nike", "Just Do It");
            for (int i = 0; i < 20; i++) {
                registerProduct(brandId, "상품_" + i, 10000L + (i * 1000), 100, 5);
            }

            // warm-up
            for (int i = 0; i < WARM_UP_COUNT; i++) {
                getProducts(null, brandId, "LATEST", 0, 20);
                cacheManager.getCache("productList").clear();
            }

            // --- 캐시 미스 측정 ---
            long[] cacheMissTimes = new long[MEASURE_COUNT];
            for (int i = 0; i < MEASURE_COUNT; i++) {
                cacheManager.getCache("productList").clear();
                long start = System.nanoTime();
                getProducts(null, brandId, "LATEST", 0, 20);
                cacheMissTimes[i] = (System.nanoTime() - start) / 1_000_000;
            }

            // --- 캐시 히트 측정 ---
            getProducts(null, brandId, "LATEST", 0, 20); // 캐시 저장
            long[] cacheHitTimes = new long[MEASURE_COUNT];
            for (int i = 0; i < MEASURE_COUNT; i++) {
                long start = System.nanoTime();
                getProducts(null, brandId, "LATEST", 0, 20);
                cacheHitTimes[i] = (System.nanoTime() - start) / 1_000_000;
            }

            // 결과 계산
            long cacheMissAvg = average(cacheMissTimes);
            long cacheHitAvg = average(cacheHitTimes);
            double improvementRate = cacheMissAvg > 0
                ? ((double) (cacheMissAvg - cacheHitAvg) / cacheMissAvg) * 100 : 0;

            // 결과 출력
            log.info("========== 상품 목록 캐시 성능 측정 결과 ==========");
            log.info("캐시 미스 (DB 조회): 평균 {}ms  (각 회차: {})", cacheMissAvg, formatTimes(cacheMissTimes));
            log.info("캐시 히트 (Caffeine): 평균 {}ms  (각 회차: {})", cacheHitAvg, formatTimes(cacheHitTimes));
            log.info("개선율: {}% ({}ms → {}ms)", String.format("%.1f", improvementRate), cacheMissAvg, cacheHitAvg);
            log.info("================================================");

            // assert
            assertThat(cacheHitAvg).isLessThan(cacheMissAvg);
        }

        private long average(long[] times) {
            long sum = 0;
            for (long t : times) sum += t;
            return sum / times.length;
        }

        private String formatTimes(long[] times) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < times.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(times[i]).append("ms");
            }
            return sb.append("]").toString();
        }
    }

    // --- helper methods ---

    private ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> getProduct(Long productId) {
        return testRestTemplate.exchange(
            PRODUCT_PUBLIC + "/" + productId,
            HttpMethod.GET,
            adminEntity(null),
            new ParameterizedTypeReference<>() {}
        );
    }

    private void getProducts(String keyword, Long brandId, String sort, int page, int size) {
        String url = PRODUCT_PUBLIC + "?sort=" + sort + "&page=" + page + "&size=" + size;
        if (keyword != null) {
            url += "&keyword=" + keyword;
        }
        if (brandId != null) {
            url += "&brandId=" + brandId;
        }
        testRestTemplate.exchange(
            url,
            HttpMethod.GET,
            adminEntity(null),
            new ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductListResponse>>() {}
        );
    }

    private <T> HttpEntity<T> adminEntity(T body) {
        return new HttpEntity<>(body, adminHeaders());
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        headers.set("Content-Type", "application/json");
        return headers;
    }

    private Long registerBrand(String name, String description) {
        var request = new BrandV1Dto.RegisterRequest(name, description);
        ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
            BRAND_ADMIN,
            HttpMethod.POST,
            adminEntity(request),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().brand().id();
    }

    private Long registerProduct(Long brandId, String name, Long price, int stock, int maxOrder) {
        var request = new ProductV1Dto.RegisterRequest(brandId, name, "설명", price, stock, maxOrder);
        ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
            PRODUCT_ADMIN,
            HttpMethod.POST,
            adminEntity(request),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().product().id();
    }
}

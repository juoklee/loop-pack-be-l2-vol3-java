package com.loopers.infrastructure.product;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.product.ProductV1Dto;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 상품 조회 읽기 최적화 종합 성능 테스트 (100만건)
 *
 * 3단계 최적화를 순차 적용하며 각 단계의 정량적 개선 효과를 측정한다.
 * 1단계: 비정규화 — JOIN+GROUP BY → 비정규화 컬럼
 * 2단계: 인덱스 — Full Scan+filesort → Index Scan (SQL 레벨 + EXPLAIN 분석)
 * 3단계: 인덱스만 vs 인덱스+캐시 — API 레벨에서 실제 응답 시간 비교
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "bulk-data"})
@DisplayName("상품 조회 읽기 최적화 종합 성능 테스트 (100만건)")
class ProductReadOptimizationPerformanceTest {

    private static final Logger log = LoggerFactory.getLogger(ProductReadOptimizationPerformanceTest.class);
    private static final int WARM_UP = 3;
    private static final int MEASURE = 10;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private CacheManager cacheManager;

    // ===== 1단계: 비정규화 효과 =====

    @DisplayName("1단계: 비정규화 효과 (JOIN+GROUP BY vs 비정규화 컬럼)")
    @Nested
    class DenormalizationPerformance {

        // 비정규화 전: likes 테이블 JOIN + GROUP BY + ORDER BY count
        static final String JOIN_QUERY = """
            SELECT p.*, COUNT(l.id) as like_cnt
            FROM product p
            LEFT JOIN likes l ON l.target_id = p.id AND l.target_type = 'PRODUCT'
            WHERE p.brand_id = 1 AND p.deleted_at IS NULL
            GROUP BY p.id
            ORDER BY like_cnt DESC
            LIMIT 20
            """;

        // 비정규화 후: 단순 컬럼 조회
        static final String DENORMALIZED_QUERY = """
            SELECT * FROM product
            WHERE brand_id = 1 AND deleted_at IS NULL
            ORDER BY like_count DESC
            LIMIT 20
            """;

        @DisplayName("좋아요순 정렬: JOIN+GROUP BY vs 비정규화 컬럼 쿼리 성능 비교")
        @Test
        void joinVsDenormalized() {
            ensureLikesData();

            // warm-up
            for (int i = 0; i < WARM_UP; i++) {
                jdbcTemplate.queryForList(JOIN_QUERY);
                jdbcTemplate.queryForList(DENORMALIZED_QUERY);
            }

            long[] joinTimes = measure(() -> jdbcTemplate.queryForList(JOIN_QUERY));
            long[] denormalizedTimes = measure(() -> jdbcTemplate.queryForList(DENORMALIZED_QUERY));

            var joinExplain = jdbcTemplate.queryForList("EXPLAIN " + JOIN_QUERY);
            var denormalizedExplain = jdbcTemplate.queryForList("EXPLAIN " + DENORMALIZED_QUERY);

            long joinAvg = average(joinTimes);
            long denormalizedAvg = average(denormalizedTimes);
            double improvement = percent(joinAvg, denormalizedAvg);

            log.info("");
            log.info("╔══════════════════════════════════════════════════════════════════════╗");
            log.info("║  1단계: 비정규화 효과 (100만건 상품 + 100만건 좋아요)                ║");
            log.info("╠══════════════════════════════════════════════════════════════════════╣");
            log.info("║  쿼리: 브랜드 필터 + 좋아요순 정렬 TOP 20                            ║");
            log.info("╠══════════════════════════════════════════════════════════════════════╣");
            log.info("║  [Before] JOIN + GROUP BY + ORDER BY count                          ║");
            log.info("║    EXPLAIN: {}", joinExplain);
            log.info("║    평균: {}ms | 중앙값: {}ms | 각 회차: {}", joinAvg, median(joinTimes), formatTimes(joinTimes));
            log.info("║  [After] 비정규화 컬럼 ORDER BY like_count                          ║");
            log.info("║    EXPLAIN: {}", denormalizedExplain);
            log.info("║    평균: {}ms | 중앙값: {}ms | 각 회차: {}", denormalizedAvg, median(denormalizedTimes), formatTimes(denormalizedTimes));
            log.info("╠══════════════════════════════════════════════════════════════════════╣");
            log.info("║  개선: {}ms → {}ms (평균 {}% 개선)", joinAvg, denormalizedAvg, String.format("%.1f", improvement));
            log.info("╚══════════════════════════════════════════════════════════════════════╝");

            assertThat(denormalizedAvg).isLessThan(joinAvg);
        }

        private void ensureLikesData() {
            Integer likesCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM likes", Integer.class);
            if (likesCount != null && likesCount >= 500_000) {
                log.info("likes 데이터 이미 존재: {}건", likesCount);
                return;
            }

            log.info("likes 100만건 생성 중...");
            long start = System.currentTimeMillis();

            for (int i = 1; i <= 100; i++) {
                jdbcTemplate.update(
                    "INSERT IGNORE INTO member (login_id, password, name, birth_date, gender, email, created_at, updated_at) " +
                    "VALUES (?, 'pw', '테스트', '1990-01-01', 'MALE', ?, NOW(), NOW())",
                    "perfUser" + i, "perfUser" + i + "@test.com"
                );
            }

            var memberIds = jdbcTemplate.queryForList("SELECT id FROM member WHERE login_id LIKE 'perfUser%' ORDER BY id", Long.class);
            int batchSize = 10000;

            for (Long memberId : memberIds) {
                for (int batch = 0; batch < 10000 / batchSize; batch++) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("INSERT IGNORE INTO likes (member_id, target_type, target_id, created_at, updated_at) VALUES ");
                    for (int i = 0; i < batchSize; i++) {
                        if (i > 0) sb.append(",");
                        long targetId = (long) (Math.random() * 1_000_000) + 1;
                        sb.append(String.format("(%d, 'PRODUCT', %d, NOW(), NOW())", memberId, targetId));
                    }
                    try {
                        jdbcTemplate.execute(sb.toString());
                    } catch (Exception e) {
                        // IGNORE duplicates
                    }
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            Integer finalCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM likes", Integer.class);
            log.info("likes 생성 완료: {}건 ({}ms)", finalCount, elapsed);
        }
    }

    // ===== 2단계: 인덱스 효과 (SQL 레벨 + EXPLAIN) =====

    @DisplayName("2단계: 인덱스 효과 — SQL 레벨 EXPLAIN 분석")
    @Nested
    class IndexPerformance {

        static final String DATA_QUERY = "SELECT * FROM product WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20";
        static final String COUNT_QUERY = "SELECT COUNT(*) FROM product WHERE brand_id = 1 AND deleted_at IS NULL";

        @DisplayName("데이터 조회: 인덱스 없음 vs 인덱스 적용 (EXPLAIN 분석)")
        @Test
        void dataQueryWithoutIndexVsWithIndex() {
            dropProductIndexes();

            try {
                Map<String, Object> beforeExplain = jdbcTemplate.queryForList("EXPLAIN " + DATA_QUERY).get(0);

                for (int i = 0; i < WARM_UP; i++) {
                    jdbcTemplate.queryForList(DATA_QUERY);
                }
                long[] noIndexTimes = measure(() -> jdbcTemplate.queryForList(DATA_QUERY));

                createProductIndexes();

                Map<String, Object> afterExplain = jdbcTemplate.queryForList("EXPLAIN " + DATA_QUERY).get(0);

                for (int i = 0; i < WARM_UP; i++) {
                    jdbcTemplate.queryForList(DATA_QUERY);
                }
                long[] withIndexTimes = measure(() -> jdbcTemplate.queryForList(DATA_QUERY));

                long noIndexAvg = average(noIndexTimes);
                long withIndexAvg = average(withIndexTimes);
                double improvement = percent(noIndexAvg, withIndexAvg);

                log.info("");
                log.info("╔══════════════════════════════════════════════════════════════════════╗");
                log.info("║  2단계: 인덱스 효과 — 데이터 조회 (100만건)                          ║");
                log.info("╠══════════════════════════════════════════════════════════════════════╣");
                log.info("║  쿼리: 브랜드 필터 + 좋아요순 정렬 TOP 20                            ║");
                log.info("╠══════════════════════════════════════════════════════════════════════╣");
                log.info("║  [Before] 인덱스 없음                                               ║");
                log.info("║    type: {}, key: {}, rows: {}, Extra: {}",
                    beforeExplain.get("type"), beforeExplain.get("key"),
                    beforeExplain.get("rows"), beforeExplain.get("Extra"));
                log.info("║    평균: {}ms | 중앙값: {}ms | 각 회차: {}", noIndexAvg, median(noIndexTimes), formatTimes(noIndexTimes));
                log.info("║  [After] 인덱스 적용                                                ║");
                log.info("║    type: {}, key: {}, rows: {}, Extra: {}",
                    afterExplain.get("type"), afterExplain.get("key"),
                    afterExplain.get("rows"), afterExplain.get("Extra"));
                log.info("║    평균: {}ms | 중앙값: {}ms | 각 회차: {}", withIndexAvg, median(withIndexTimes), formatTimes(withIndexTimes));
                log.info("╠══════════════════════════════════════════════════════════════════════╣");
                log.info("║  개선: {}ms → {}ms ({} 개선, {}배 빠름)",
                    noIndexAvg, withIndexAvg, String.format("%.1f%%", improvement),
                    withIndexAvg > 0 ? noIndexAvg / withIndexAvg : "∞");
                log.info("╚══════════════════════════════════════════════════════════════════════╝");

                assertThat(withIndexAvg).isLessThan(noIndexAvg);
                assertThat(afterExplain.get("key")).isNotNull();
                assertThat(String.valueOf(afterExplain.get("Extra"))).doesNotContain("Using filesort");
            } finally {
                createProductIndexes();
            }
        }

        @DisplayName("COUNT 쿼리: 인덱스 없음 vs 인덱스 적용 (페이지네이션 비용)")
        @Test
        void countQueryWithoutIndexVsWithIndex() {
            dropProductIndexes();

            try {
                Map<String, Object> beforeExplain = jdbcTemplate.queryForList("EXPLAIN " + COUNT_QUERY).get(0);

                for (int i = 0; i < WARM_UP; i++) {
                    jdbcTemplate.queryForObject(COUNT_QUERY, Long.class);
                }
                long[] noIndexTimes = measure(() -> jdbcTemplate.queryForObject(COUNT_QUERY, Long.class));

                createProductIndexes();

                Map<String, Object> afterExplain = jdbcTemplate.queryForList("EXPLAIN " + COUNT_QUERY).get(0);

                for (int i = 0; i < WARM_UP; i++) {
                    jdbcTemplate.queryForObject(COUNT_QUERY, Long.class);
                }
                long[] withIndexTimes = measure(() -> jdbcTemplate.queryForObject(COUNT_QUERY, Long.class));

                long noIndexAvg = average(noIndexTimes);
                long withIndexAvg = average(withIndexTimes);
                double improvement = percent(noIndexAvg, withIndexAvg);

                log.info("");
                log.info("╔══════════════════════════════════════════════════════════════════════╗");
                log.info("║  2단계 보충: COUNT 쿼리 인덱스 효과 (100만건, 페이지네이션)          ║");
                log.info("╠══════════════════════════════════════════════════════════════════════╣");
                log.info("║  쿼리: SELECT COUNT(*) WHERE brand_id = 1                           ║");
                log.info("╠══════════════════════════════════════════════════════════════════════╣");
                log.info("║  [Before] 인덱스 없음                                               ║");
                log.info("║    type: {}, key: {}, rows: {}, Extra: {}",
                    beforeExplain.get("type"), beforeExplain.get("key"),
                    beforeExplain.get("rows"), beforeExplain.get("Extra"));
                log.info("║    평균: {}ms | 중앙값: {}ms | 각 회차: {}", noIndexAvg, median(noIndexTimes), formatTimes(noIndexTimes));
                log.info("║  [After] 인덱스 적용                                                ║");
                log.info("║    type: {}, key: {}, rows: {}, Extra: {}",
                    afterExplain.get("type"), afterExplain.get("key"),
                    afterExplain.get("rows"), afterExplain.get("Extra"));
                log.info("║    평균: {}ms | 중앙값: {}ms | 각 회차: {}", withIndexAvg, median(withIndexTimes), formatTimes(withIndexTimes));
                log.info("╠══════════════════════════════════════════════════════════════════════╣");
                log.info("║  개선: {}ms → {}ms ({} 개선)", noIndexAvg, withIndexAvg, String.format("%.1f%%", improvement));
                log.info("║  (페이지네이션의 총 건수 조회 — 매 목록 API 호출 시 실행됨)          ║");
                log.info("╚══════════════════════════════════════════════════════════════════════╝");

                assertThat(withIndexAvg).isLessThanOrEqualTo(noIndexAvg);
            } finally {
                createProductIndexes();
            }
        }
    }

    // ===== 3단계: 인덱스만 vs 인덱스+캐시 (API 레벨 종합 비교) =====

    @DisplayName("3단계: 인덱스만 vs 인덱스+캐시 — API 응답 시간 비교")
    @Nested
    class IndexVsIndexPlusCachePerformance {

        @DisplayName("상품 목록 API: 인덱스만 vs 인덱스+캐시")
        @Test
        void productListApi_indexOnly_vs_indexPlusCache() {
            createProductIndexes();

            String url = "/api/v1/products?brandId=1&sort=LIKES_DESC&page=0&size=20";
            var type = new ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductListResponse>>() {};

            // warm-up
            for (int i = 0; i < WARM_UP; i++) {
                testRestTemplate.exchange(url, HttpMethod.GET, adminEntity(), type);
                clearAllCaches();
            }

            // ===== 인덱스만 (매번 캐시 비워서 DB 직접 조회) =====
            long[] indexOnlyTimes = new long[MEASURE];
            for (int i = 0; i < MEASURE; i++) {
                clearAllCaches();
                long start = System.nanoTime();
                testRestTemplate.exchange(url, HttpMethod.GET, adminEntity(), type);
                indexOnlyTimes[i] = (System.nanoTime() - start) / 1_000_000;
            }

            // ===== 인덱스+캐시 (캐시 적재 후 캐시 히트) =====
            clearAllCaches();
            testRestTemplate.exchange(url, HttpMethod.GET, adminEntity(), type); // 캐시 적재
            long[] indexPlusCacheTimes = new long[MEASURE];
            for (int i = 0; i < MEASURE; i++) {
                long start = System.nanoTime();
                testRestTemplate.exchange(url, HttpMethod.GET, adminEntity(), type);
                indexPlusCacheTimes[i] = (System.nanoTime() - start) / 1_000_000;
            }

            long indexOnlyAvg = average(indexOnlyTimes);
            long indexPlusCacheAvg = average(indexPlusCacheTimes);
            double cacheImprovement = percent(indexOnlyAvg, indexPlusCacheAvg);

            log.info("");
            log.info("╔══════════════════════════════════════════════════════════════════════╗");
            log.info("║  3단계: 인덱스만 vs 인덱스+캐시 — 상품 목록 API (100만건)            ║");
            log.info("╠══════════════════════════════════════════════════════════════════════╣");
            log.info("║  API: GET /api/v1/products?brandId=1&sort=LIKES_DESC&size=20         ║");
            log.info("╠══════════════════════════════════════════════════════════════════════╣");
            log.info("║  [인덱스만] DB 조회 (캐시 미사용)                                    ║");
            log.info("║    평균: {}ms | 중앙값: {}ms | 각 회차: {}", indexOnlyAvg, median(indexOnlyTimes), formatTimes(indexOnlyTimes));
            log.info("║  [인덱스+캐시] Caffeine 캐시 히트                                    ║");
            log.info("║    평균: {}ms | 중앙값: {}ms | 각 회차: {}", indexPlusCacheAvg, median(indexPlusCacheTimes), formatTimes(indexPlusCacheTimes));
            log.info("╠══════════════════════════════════════════════════════════════════════╣");
            log.info("║  캐시 추가 개선: {}ms → {}ms ({} 개선, {}배 빠름)",
                indexOnlyAvg, indexPlusCacheAvg, String.format("%.1f%%", cacheImprovement),
                indexPlusCacheAvg > 0 ? indexOnlyAvg / indexPlusCacheAvg : "∞");
            log.info("╚══════════════════════════════════════════════════════════════════════╝");

            assertThat(indexPlusCacheAvg).isLessThan(indexOnlyAvg);
        }

        @DisplayName("상품 상세 API: 인덱스만 vs 인덱스+캐시")
        @Test
        void productDetailApi_indexOnly_vs_indexPlusCache() {
            String url = "/api/v1/products/1";
            var type = new ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>>() {};

            // warm-up
            for (int i = 0; i < WARM_UP; i++) {
                testRestTemplate.exchange(url, HttpMethod.GET, adminEntity(), type);
                clearAllCaches();
            }

            // ===== 인덱스만 (매번 캐시 비워서 DB 직접 조회) =====
            long[] indexOnlyTimes = new long[MEASURE];
            for (int i = 0; i < MEASURE; i++) {
                clearAllCaches();
                long start = System.nanoTime();
                testRestTemplate.exchange(url, HttpMethod.GET, adminEntity(), type);
                indexOnlyTimes[i] = (System.nanoTime() - start) / 1_000_000;
            }

            // ===== 인덱스+캐시 (캐시 적재 후 캐시 히트) =====
            clearAllCaches();
            testRestTemplate.exchange(url, HttpMethod.GET, adminEntity(), type); // 캐시 적재
            long[] indexPlusCacheTimes = new long[MEASURE];
            for (int i = 0; i < MEASURE; i++) {
                long start = System.nanoTime();
                testRestTemplate.exchange(url, HttpMethod.GET, adminEntity(), type);
                indexPlusCacheTimes[i] = (System.nanoTime() - start) / 1_000_000;
            }

            long indexOnlyAvg = average(indexOnlyTimes);
            long indexPlusCacheAvg = average(indexPlusCacheTimes);
            double cacheImprovement = percent(indexOnlyAvg, indexPlusCacheAvg);

            log.info("");
            log.info("╔══════════════════════════════════════════════════════════════════════╗");
            log.info("║  3단계: 인덱스만 vs 인덱스+캐시 — 상품 상세 API                      ║");
            log.info("╠══════════════════════════════════════════════════════════════════════╣");
            log.info("║  API: GET /api/v1/products/1 (PK 조회)                               ║");
            log.info("╠══════════════════════════════════════════════════════════════════════╣");
            log.info("║  [인덱스만] DB 조회 (캐시 미사용)                                    ║");
            log.info("║    평균: {}ms | 중앙값: {}ms | 각 회차: {}", indexOnlyAvg, median(indexOnlyTimes), formatTimes(indexOnlyTimes));
            log.info("║  [인덱스+캐시] Caffeine 캐시 히트                                    ║");
            log.info("║    평균: {}ms | 중앙값: {}ms | 각 회차: {}", indexPlusCacheAvg, median(indexPlusCacheTimes), formatTimes(indexPlusCacheTimes));
            log.info("╠══════════════════════════════════════════════════════════════════════╣");
            log.info("║  캐시 추가 개선: {}ms → {}ms ({} 개선, {}배 빠름)",
                indexOnlyAvg, indexPlusCacheAvg, String.format("%.1f%%", cacheImprovement),
                indexPlusCacheAvg > 0 ? indexOnlyAvg / indexPlusCacheAvg : "∞");
            log.info("║  (상세 조회는 PK 기반 — 인덱스 추가 효과 없음, 캐시만 유효)          ║");
            log.info("╚══════════════════════════════════════════════════════════════════════╝");

            assertThat(indexPlusCacheAvg).isLessThan(indexOnlyAvg);
        }
    }

    // ===== 공통 유틸 =====

    private void clearAllCaches() {
        cacheManager.getCache("productDetail").clear();
        cacheManager.getCache("productList").clear();
    }

    private void dropProductIndexes() {
        String[] indexes = {"idx_product_brand_like", "idx_product_brand_price", "idx_product_like_count", "idx_product_created_at"};
        for (String idx : indexes) {
            try { jdbcTemplate.execute("DROP INDEX " + idx + " ON product"); } catch (Exception ignored) {}
        }
    }

    private void createProductIndexes() {
        try { jdbcTemplate.execute("CREATE INDEX idx_product_brand_like ON product (brand_id, like_count DESC)"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("CREATE INDEX idx_product_brand_price ON product (brand_id, price)"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("CREATE INDEX idx_product_like_count ON product (like_count DESC)"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("CREATE INDEX idx_product_created_at ON product (created_at DESC)"); } catch (Exception ignored) {}
    }

    private long[] measure(Runnable action) {
        long[] times = new long[MEASURE];
        for (int i = 0; i < MEASURE; i++) {
            long start = System.nanoTime();
            action.run();
            times[i] = (System.nanoTime() - start) / 1_000_000;
        }
        return times;
    }

    private HttpEntity<Void> adminEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        return new HttpEntity<>(headers);
    }

    private long average(long[] times) {
        long sum = 0;
        for (long t : times) sum += t;
        return sum / times.length;
    }

    private long median(long[] times) {
        long[] sorted = Arrays.copyOf(times, times.length);
        Arrays.sort(sorted);
        int mid = sorted.length / 2;
        if (sorted.length % 2 == 0) {
            return (sorted[mid - 1] + sorted[mid]) / 2;
        }
        return sorted[mid];
    }

    private double percent(long before, long after) {
        return before > 0 ? ((double) (before - after) / before) * 100 : 0;
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

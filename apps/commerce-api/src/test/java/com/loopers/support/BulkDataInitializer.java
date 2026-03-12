package com.loopers.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Profile("bulk-data")
public class BulkDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BulkDataInitializer.class);

    private static final int BRAND_COUNT = 200;
    private static final int PRODUCTS_PER_BRAND = 5_000;
    private static final int TOTAL_PRODUCTS = BRAND_COUNT * PRODUCTS_PER_BRAND;

    private final JdbcTemplate jdbcTemplate;

    public BulkDataInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer existingCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product", Integer.class);
        if (existingCount != null && existingCount >= TOTAL_PRODUCTS) {
            log.info("이미 {}건의 상품 데이터가 존재합니다. 스킵합니다.", existingCount);
            return;
        }

        log.info("========== 100만건 테스트 데이터 생성 시작 ==========");
        long startTime = System.currentTimeMillis();

        insertBrands();
        insertProducts();

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("========== 100만건 데이터 생성 완료: {}ms ==========", elapsed);

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product", Integer.class);
        log.info("product 테이블 row 수: {}", count);
    }

    private void insertBrands() {
        log.info("브랜드 {}개 생성 중...", BRAND_COUNT);
        String sql = "INSERT INTO brand (name, description, like_count, created_at, updated_at) VALUES (?, ?, ?, NOW(), NOW())";

        for (int i = 1; i <= BRAND_COUNT; i++) {
            jdbcTemplate.update(sql,
                    "Brand_" + String.format("%03d", i),
                    "브랜드 " + i + " 설명입니다.",
                    ThreadLocalRandom.current().nextInt(0, 500)
            );
        }
        log.info("브랜드 생성 완료");
    }

    private void insertProducts() {
        log.info("상품 {}건 생성 중...", TOTAL_PRODUCTS);
        String sql = "INSERT INTO product (brand_id, name, description, price, stock_quantity, max_order_quantity, like_count, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, DATE_SUB(NOW(), INTERVAL ? DAY), NOW())";

        String[] categories = {"티셔츠", "바지", "원피스", "자켓", "코트", "니트", "셔츠", "스커트"};
        String[] adjectives = {"클래식", "모던", "빈티지", "캐주얼", "프리미엄", "베이직"};

        List<Long> brandIds = jdbcTemplate.queryForList("SELECT id FROM brand ORDER BY id", Long.class);

        int totalInserted = 0;

        for (int brandIdx = 0; brandIdx < brandIds.size(); brandIdx++) {
            Long brandId = brandIds.get(brandIdx);
            final int currentBrandIdx = brandIdx;
            var random = ThreadLocalRandom.current();

            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    String category = categories[random.nextInt(categories.length)];
                    String adj = adjectives[random.nextInt(adjectives.length)];
                    long price = 10000 + random.nextLong(490000);
                    int likeCount = (int) (Math.pow(random.nextDouble(), 4) * 10000);
                    int stock = random.nextInt(1000);
                    int daysAgo = random.nextInt(365);

                    ps.setLong(1, brandId);
                    ps.setString(2, adj + " " + category + " #" + (currentBrandIdx * PRODUCTS_PER_BRAND + i + 1));
                    ps.setString(3, "Brand_" + String.format("%03d", currentBrandIdx + 1) + "의 " + adj + " " + category + " 상품입니다.");
                    ps.setLong(4, price);
                    ps.setInt(5, stock);
                    ps.setInt(6, 5);
                    ps.setInt(7, likeCount);
                    ps.setInt(8, daysAgo);
                }

                @Override
                public int getBatchSize() {
                    return PRODUCTS_PER_BRAND;
                }
            });

            totalInserted += PRODUCTS_PER_BRAND;
            if ((brandIdx + 1) % 50 == 0) {
                log.info("진행률: {}/{} ({}%)", totalInserted, TOTAL_PRODUCTS, (totalInserted * 100) / TOTAL_PRODUCTS);
            }
        }
        log.info("상품 생성 완료: {}건", totalInserted);
    }

    public int getTotalProducts() {
        return TOTAL_PRODUCTS;
    }
}

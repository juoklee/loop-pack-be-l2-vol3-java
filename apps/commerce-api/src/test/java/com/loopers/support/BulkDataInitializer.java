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

    private static final String SEED_PREFIX = "SEED_";
    private static final int BRAND_COUNT = 200;
    private static final int PRODUCTS_PER_BRAND = 5_000;
    private static final int TOTAL_PRODUCTS = BRAND_COUNT * PRODUCTS_PER_BRAND;

    private final JdbcTemplate jdbcTemplate;

    public BulkDataInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer existingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product p JOIN brand b ON p.brand_id = b.id WHERE b.name LIKE ?",
                Integer.class, SEED_PREFIX + "%");
        if (existingCount != null && existingCount >= TOTAL_PRODUCTS) {
            log.info("이미 시드 상품 데이터 {}건이 존재합니다. 스킵합니다.", existingCount);
            return;
        }

        log.info("========== 시드 데이터 생성 시작 (목표: {}건) ==========", TOTAL_PRODUCTS);
        long startTime = System.currentTimeMillis();

        insertBrands();
        insertProducts();

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("========== 시드 데이터 생성 완료: {}ms ==========", elapsed);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product p JOIN brand b ON p.brand_id = b.id WHERE b.name LIKE ?",
                Integer.class, SEED_PREFIX + "%");
        log.info("시드 상품 row 수: {}", count);
    }

    private void insertBrands() {
        log.info("시드 브랜드 {}개 생성 중...", BRAND_COUNT);

        List<String> existingNames = jdbcTemplate.queryForList(
                "SELECT name FROM brand WHERE name LIKE ?", String.class, SEED_PREFIX + "%");

        String sql = "INSERT INTO brand (name, description, like_count, created_at, updated_at) VALUES (?, ?, ?, NOW(), NOW())";
        int created = 0;

        for (int i = 1; i <= BRAND_COUNT; i++) {
            String name = SEED_PREFIX + "Brand_" + String.format("%03d", i);
            if (existingNames.contains(name)) {
                continue;
            }
            jdbcTemplate.update(sql,
                    name,
                    "시드 브랜드 " + i + " 설명입니다.",
                    ThreadLocalRandom.current().nextInt(0, 500)
            );
            created++;
        }
        log.info("시드 브랜드 생성 완료 (신규: {}개, 기존: {}개)", created, BRAND_COUNT - created);
    }

    private void insertProducts() {
        log.info("상품 {}건 생성 중...", TOTAL_PRODUCTS);
        String sql = "INSERT INTO product (brand_id, name, description, price, stock_quantity, max_order_quantity, like_count, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, DATE_SUB(NOW(), INTERVAL ? DAY), NOW())";

        String[] categories = {"티셔츠", "바지", "원피스", "자켓", "코트", "니트", "셔츠", "스커트"};
        String[] adjectives = {"클래식", "모던", "빈티지", "캐주얼", "프리미엄", "베이직"};

        List<Long> brandIds = jdbcTemplate.queryForList(
                "SELECT id FROM brand WHERE name LIKE ? ORDER BY id", Long.class, SEED_PREFIX + "%");

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
                    ps.setString(3, SEED_PREFIX + "Brand_" + String.format("%03d", currentBrandIdx + 1) + "의 " + adj + " " + category + " 상품입니다.");
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

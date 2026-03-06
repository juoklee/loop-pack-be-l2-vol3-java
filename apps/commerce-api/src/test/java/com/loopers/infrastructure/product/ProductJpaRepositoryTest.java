package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductJpaRepositoryTest {

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Product createAndSaveProduct() {
        Product product = Product.create(1L, "테스트 상품", "설명", 10000L, 100, 5);
        return productJpaRepository.save(product);
    }

    @DisplayName("increaseLikeCount")
    @Nested
    class IncreaseLikeCount {

        @DisplayName("soft-delete된 상품은 좋아요 수가 증가하지 않는다.")
        @Test
        @Transactional
        void doesNotIncrease_whenProductIsSoftDeleted() {
            // arrange
            Product product = createAndSaveProduct();
            product.delete();
            productJpaRepository.saveAndFlush(product);

            // act
            int updatedRows = productJpaRepository.increaseLikeCount(product.getId());

            // assert
            assertThat(updatedRows).isZero();
        }

        @DisplayName("존재하지 않는 상품 ID는 좋아요 수가 증가하지 않는다.")
        @Test
        @Transactional
        void doesNotIncrease_whenProductDoesNotExist() {
            // arrange
            Long nonExistentId = 999L;

            // act
            int updatedRows = productJpaRepository.increaseLikeCount(nonExistentId);

            // assert
            assertThat(updatedRows).isZero();
        }

        @DisplayName("정상 상품은 좋아요 수가 증가한다.")
        @Test
        @Transactional
        void increases_whenProductExists() {
            // arrange
            Product product = createAndSaveProduct();

            // act
            int updatedRows = productJpaRepository.increaseLikeCount(product.getId());

            // assert
            assertThat(updatedRows).isEqualTo(1);
        }
    }

    @DisplayName("decreaseLikeCount")
    @Nested
    class DecreaseLikeCount {

        @DisplayName("soft-delete된 상품은 좋아요 수가 감소하지 않는다.")
        @Test
        @Transactional
        void doesNotDecrease_whenProductIsSoftDeleted() {
            // arrange
            Product product = createAndSaveProduct();
            productJpaRepository.increaseLikeCount(product.getId());
            product.delete();
            productJpaRepository.saveAndFlush(product);

            // act
            int updatedRows = productJpaRepository.decreaseLikeCount(product.getId());

            // assert
            assertThat(updatedRows).isZero();
        }

        @DisplayName("존재하지 않는 상품 ID는 좋아요 수가 감소하지 않는다.")
        @Test
        @Transactional
        void doesNotDecrease_whenProductDoesNotExist() {
            // arrange
            Long nonExistentId = 999L;

            // act
            int updatedRows = productJpaRepository.decreaseLikeCount(nonExistentId);

            // assert
            assertThat(updatedRows).isZero();
        }

        @DisplayName("정상 상품은 좋아요 수가 감소한다.")
        @Test
        @Transactional
        void decreases_whenProductExistsAndLikeCountIsPositive() {
            // arrange
            Product product = createAndSaveProduct();
            productJpaRepository.increaseLikeCount(product.getId());

            // act
            int updatedRows = productJpaRepository.decreaseLikeCount(product.getId());

            // assert
            assertThat(updatedRows).isEqualTo(1);
        }
    }
}

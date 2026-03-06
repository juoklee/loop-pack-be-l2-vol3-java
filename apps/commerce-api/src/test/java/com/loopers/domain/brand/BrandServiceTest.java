package com.loopers.domain.brand;

import com.loopers.domain.PageResult;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.loopers.domain.PageResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrandServiceTest {

    private BrandService brandService;
    private FakeBrandReader fakeBrandReader;
    private FakeBrandRepository fakeBrandRepository;

    @BeforeEach
    void setUp() {
        fakeBrandReader = new FakeBrandReader();
        fakeBrandRepository = new FakeBrandRepository();
        brandService = new BrandService(fakeBrandReader, fakeBrandRepository);
    }

    @DisplayName("브랜드를 등록할 때, ")
    @Nested
    class Register {

        @DisplayName("유효한 정보로 등록하면, 브랜드가 저장된다.")
        @Test
        void savesBrand_whenFieldsAreValid() {
            // Arrange & Act
            Brand brand = brandService.register("Nike", "Just Do It");

            // Assert
            assertAll(
                () -> assertThat(brand.getName()).isEqualTo("Nike"),
                () -> assertThat(brand.getDescription()).isEqualTo("Just Do It"),
                () -> assertThat(brand.getLikeCount()).isZero()
            );
        }

        @DisplayName("이미 존재하는 브랜드명으로 등록하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameAlreadyExists() {
            // Arrange
            fakeBrandReader.addExistingName("Nike");

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                brandService.register("Nike", "Just Do It");
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("브랜드를 조회할 때, ")
    @Nested
    class GetBrand {

        @DisplayName("존재하는 브랜드를 조회하면, 브랜드를 반환한다.")
        @Test
        void returnsBrand_whenExists() {
            // Arrange
            Brand saved = fakeBrandRepository.save(Brand.create("Nike", "Just Do It"));
            fakeBrandReader.addBrand(1L, saved);

            // Act
            Brand found = brandService.getBrand(1L);

            // Assert
            assertThat(found.getName()).isEqualTo("Nike");
        }

        @DisplayName("존재하지 않는 브랜드를 조회하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotExists() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                brandService.getBrand(999L);
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드를 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("유효한 정보로 수정하면, 브랜드가 수정된다.")
        @Test
        void updatesBrand_whenFieldsAreValid() {
            // Arrange
            Brand brand = Brand.create("Nike", "Just Do It");
            fakeBrandReader.addBrand(1L, brand);

            // Act
            brandService.updateInfo(1L, "Adidas", "Impossible Is Nothing");

            // Assert
            assertAll(
                () -> assertThat(brand.getName()).isEqualTo("Adidas"),
                () -> assertThat(brand.getDescription()).isEqualTo("Impossible Is Nothing")
            );
        }

        @DisplayName("존재하지 않는 브랜드를 수정하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotExists() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                brandService.updateInfo(999L, "Adidas", "설명");
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드를 삭제할 때, ")
    @Nested
    class Delete {

        @DisplayName("존재하는 브랜드를 삭제하면, soft delete 처리된다.")
        @Test
        void deletesBrand_whenExists() {
            // Arrange
            Brand brand = Brand.create("Nike", "Just Do It");
            fakeBrandReader.addBrand(1L, brand);

            // Act
            brandService.delete(1L);

            // Assert
            assertThat(brand.getDeletedAt()).isNotNull();
        }

        @DisplayName("존재하지 않는 브랜드를 삭제하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotExists() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                brandService.delete(999L);
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드 목록을 조회할 때, ")
    @Nested
    class GetBrands {

        @DisplayName("키워드 없이 조회하면, 전체 브랜드를 반환한다.")
        @Test
        void returnsAllBrands_whenNoKeyword() {
            // Arrange
            Brand nike = Brand.create("Nike", "Just Do It");
            Brand adidas = Brand.create("Adidas", "Impossible Is Nothing");
            fakeBrandReader.addBrands(List.of(nike, adidas));

            // Act
            PageResult<Brand> result = brandService.getBrands(null, 0, 20);

            // Assert
            assertThat(result.content()).hasSize(2);
        }
    }

    @DisplayName("브랜드 좋아요 수를 증가할 때, ")
    @Nested
    class IncreaseLikeCount {

        @DisplayName("존재하는 브랜드의 좋아요를 증가하면, 정상 처리된다.")
        @Test
        void succeeds_whenBrandExists() {
            // Arrange
            fakeBrandRepository.save(Brand.create("Nike", "Just Do It"));

            // Act & Assert (예외 없이 정상 수행)
            brandService.increaseLikeCount(1L);
        }

        @DisplayName("존재하지 않는 브랜드의 좋아요를 증가하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandNotExists() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                brandService.increaseLikeCount(999L);
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드 좋아요 수를 감소할 때, ")
    @Nested
    class DecreaseLikeCount {

        @DisplayName("존재하는 브랜드의 좋아요를 감소하면, 정상 처리된다.")
        @Test
        void succeeds_whenBrandExists() {
            // Arrange
            fakeBrandRepository.save(Brand.create("Nike", "Just Do It"));

            // Act & Assert (예외 없이 정상 수행)
            brandService.decreaseLikeCount(1L);
        }

        @DisplayName("존재하지 않는 브랜드의 좋아요를 감소하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandNotExists() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                brandService.decreaseLikeCount(999L);
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    // Fake 구현체
    static class FakeBrandReader implements BrandReader {
        private final Map<Long, Brand> brands = new HashMap<>();
        private final Map<String, Boolean> existingNames = new HashMap<>();
        private List<Brand> allBrands = List.of();

        void addBrand(Long id, Brand brand) {
            brands.put(id, brand);
        }

        void addExistingName(String name) {
            existingNames.put(name, true);
        }

        void addBrands(List<Brand> brands) {
            this.allBrands = brands;
        }

        @Override
        public Optional<Brand> findById(Long id) {
            return Optional.ofNullable(brands.get(id));
        }

        @Override
        public boolean existsById(Long id) {
            return brands.containsKey(id);
        }

        @Override
        public boolean existsByName(String name) {
            return existingNames.containsKey(name);
        }

        @Override
        public List<Brand> findAllByIds(List<Long> ids) {
            return ids.stream()
                .map(brands::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        }

        @Override
        public PageResult<Brand> findAll(String keyword, int page, int size) {
            return new PageResult<>(allBrands, allBrands.size(), 1, page, size);
        }
    }

    static class FakeBrandRepository implements BrandRepository {
        private final Map<Long, Brand> brands = new HashMap<>();
        private long idSequence = 1L;

        @Override
        public Brand save(Brand brand) {
            Long id = idSequence++;
            brands.put(id, brand);
            return brand;
        }

        @Override
        public int increaseLikeCount(Long id) {
            return brands.containsKey(id) ? 1 : 0;
        }

        @Override
        public int decreaseLikeCount(Long id) {
            return brands.containsKey(id) ? 1 : 0;
        }
    }
}

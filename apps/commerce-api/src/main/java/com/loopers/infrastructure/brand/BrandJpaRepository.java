package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.Brand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BrandJpaRepository extends JpaRepository<Brand, Long> {
    Optional<Brand> findByIdAndDeletedAtIsNull(Long id);
    boolean existsByIdAndDeletedAtIsNull(Long id);
    boolean existsByNameAndDeletedAtIsNull(String name);
    List<Brand> findAllByIdInAndDeletedAtIsNull(List<Long> ids);
    Page<Brand> findByDeletedAtIsNull(Pageable pageable);
    Page<Brand> findByNameContainingAndDeletedAtIsNull(String keyword, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Brand b SET b.likeCount = b.likeCount + 1 WHERE b.id = :id AND b.deletedAt IS NULL")
    int increaseLikeCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Brand b SET b.likeCount = b.likeCount - 1 WHERE b.id = :id AND b.deletedAt IS NULL AND b.likeCount > 0")
    int decreaseLikeCount(@Param("id") Long id);
}

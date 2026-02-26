package com.loopers.infrastructure.product;

import com.loopers.domain.brand.QBrand;
import com.loopers.domain.PageResult;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductReader;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.product.QProduct;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductReaderImpl implements ProductReader {

    private final ProductJpaRepository productJpaRepository;
    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<Product> findById(Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public PageResult<Product> findAll(String keyword, Long brandId, ProductSortType sort, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        QProduct product = QProduct.product;
        QBrand brand = QBrand.brand;

        BooleanBuilder where = new BooleanBuilder();
        where.and(product.deletedAt.isNull());

        if (brandId != null) {
            where.and(product.brandId.eq(brandId));
        }

        boolean needsJoin = keyword != null && !keyword.isBlank();
        if (needsJoin) {
            where.and(
                product.name.containsIgnoreCase(keyword)
                    .or(brand.name.containsIgnoreCase(keyword))
            );
        }

        OrderSpecifier<?> orderSpecifier = resolveSort(sort, product);

        var query = queryFactory.selectFrom(product);
        if (needsJoin) {
            query.leftJoin(brand).on(brand.id.eq(product.brandId).and(brand.deletedAt.isNull()));
        }

        List<Product> content = query
            .where(where)
            .orderBy(orderSpecifier)
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        var countQuery = queryFactory.select(product.count()).from(product);
        if (needsJoin) {
            countQuery.leftJoin(brand).on(brand.id.eq(product.brandId).and(brand.deletedAt.isNull()));
        }
        Long total = countQuery.where(where).fetchOne();

        long totalCount = total != null ? total : 0L;
        int totalPages = (int) Math.ceil((double) totalCount / size);
        return new PageResult<>(content, totalCount, totalPages, page, size);
    }

    @Override
    public List<Product> findAllByIds(List<Long> ids) {
        return productJpaRepository.findAllByIdInAndDeletedAtIsNull(ids);
    }

    @Override
    public List<Product> findAllByBrandId(Long brandId) {
        return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId);
    }

    private OrderSpecifier<?> resolveSort(ProductSortType sort, QProduct product) {
        if (sort == null) {
            return product.createdAt.desc();
        }
        return switch (sort) {
            case LATEST -> product.createdAt.desc();
            case PRICE_ASC -> product.price.asc();
            case PRICE_DESC -> product.price.desc();
            case LIKES_DESC -> product.likeCount.desc();
        };
    }
}

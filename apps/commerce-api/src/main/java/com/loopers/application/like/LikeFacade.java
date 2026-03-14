package com.loopers.application.like;

import com.loopers.application.PagedInfo;
import com.loopers.domain.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.like.LikeTargetType;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final MemberService memberService;
    private final ProductService productService;
    private final BrandService brandService;
    private final LikeService likeService;

    @CacheEvict(cacheNames = "productDetail", key = "#productId")
    @Transactional
    public LikeToggleInfo toggleProductLike(String loginId, Long productId) {
        Long memberId = getMemberId(loginId);
        productService.getProduct(productId);

        boolean liked = likeService.toggleLike(memberId, LikeTargetType.PRODUCT, productId);

        if (liked) {
            productService.increaseLikeCount(productId);
        } else {
            productService.decreaseLikeCount(productId);
        }

        Product product = productService.getProduct(productId);
        return new LikeToggleInfo(liked, product.getLikeCount());
    }

    @Transactional
    public LikeToggleInfo toggleBrandLike(String loginId, Long brandId) {
        Long memberId = getMemberId(loginId);
        brandService.getBrand(brandId);

        boolean liked = likeService.toggleLike(memberId, LikeTargetType.BRAND, brandId);

        if (liked) {
            brandService.increaseLikeCount(brandId);
        } else {
            brandService.decreaseLikeCount(brandId);
        }

        Brand brand = brandService.getBrand(brandId);
        return new LikeToggleInfo(liked, brand.getLikeCount());
    }

    @Transactional(readOnly = true)
    public PagedInfo<ProductLikeInfo> getMyLikedProducts(String loginId, int page, int size) {
        Long memberId = getMemberId(loginId);
        PageResult<Like> likes = likeService.getMyLikes(memberId, LikeTargetType.PRODUCT, page, size);

        List<Long> productIds = likes.content().stream()
            .map(Like::getTargetId).toList();
        Map<Long, Product> productMap = productService.getProductsByIds(productIds).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<Long> brandIds = productMap.values().stream()
            .map(Product::getBrandId).distinct().toList();
        Map<Long, Brand> brandMap = brandService.getBrandsByIds(brandIds).stream()
            .collect(Collectors.toMap(Brand::getId, Function.identity()));

        List<ProductLikeInfo> content = likes.content().stream()
            .filter(like -> productMap.containsKey(like.getTargetId()))
            .map(like -> {
                Product product = productMap.get(like.getTargetId());
                Brand brand = brandMap.get(product.getBrandId());
                return ProductLikeInfo.of(like, product, brand);
            })
            .toList();

        return new PagedInfo<>(content, likes.totalElements(), likes.totalPages(), likes.page(), likes.size());
    }

    @Transactional(readOnly = true)
    public PagedInfo<BrandLikeInfo> getMyLikedBrands(String loginId, int page, int size) {
        Long memberId = getMemberId(loginId);
        PageResult<Like> likes = likeService.getMyLikes(memberId, LikeTargetType.BRAND, page, size);

        List<Long> brandIds = likes.content().stream()
            .map(Like::getTargetId).toList();
        Map<Long, Brand> brandMap = brandService.getBrandsByIds(brandIds).stream()
            .collect(Collectors.toMap(Brand::getId, Function.identity()));

        List<BrandLikeInfo> content = likes.content().stream()
            .filter(like -> brandMap.containsKey(like.getTargetId()))
            .map(like -> {
                Brand brand = brandMap.get(like.getTargetId());
                return BrandLikeInfo.of(like, brand);
            })
            .toList();

        return new PagedInfo<>(content, likes.totalElements(), likes.totalPages(), likes.page(), likes.size());
    }

    private Long getMemberId(String loginId) {
        Member member = memberService.getMemberByLoginId(loginId);
        return member.getId();
    }
}

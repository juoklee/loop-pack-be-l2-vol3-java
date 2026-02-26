package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "product")
public class Product extends BaseEntity {

    @Column(nullable = false)
    private Long brandId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private Long price;

    @Column(nullable = false)
    private int stockQuantity;

    @Column(nullable = false)
    private int maxOrderQuantity;

    @Column(nullable = false)
    private int likeCount;

    protected Product() {}

    private Product(Long brandId, String name, String description, Long price, int stockQuantity, int maxOrderQuantity) {
        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.maxOrderQuantity = maxOrderQuantity;
        this.likeCount = 0;
    }

    public static Product create(Long brandId, String name, String description, Long price, int stockQuantity, int maxOrderQuantity) {
        validateNotBlank(name, "상품명은 필수입니다.");
        validatePositive(price, "가격은 0보다 커야 합니다.");
        validateNotNegative(stockQuantity, "재고 수량은 0 이상이어야 합니다.");
        validatePositive((long) maxOrderQuantity, "최대 주문 수량은 0보다 커야 합니다.");
        return new Product(brandId, name, description, price, stockQuantity, maxOrderQuantity);
    }

    public void update(String name, String description, Long price, int maxOrderQuantity) {
        validateNotBlank(name, "상품명은 필수입니다.");
        validatePositive(price, "가격은 0보다 커야 합니다.");
        validatePositive((long) maxOrderQuantity, "최대 주문 수량은 0보다 커야 합니다.");
        this.name = name;
        this.description = description;
        this.price = price;
        this.maxOrderQuantity = maxOrderQuantity;
    }

    public void updateStock(int quantity) {
        validateNotNegative(quantity, "재고 수량은 0 이상이어야 합니다.");
        this.stockQuantity = quantity;
    }

    public void decreaseStock(int quantity) {
        if (this.stockQuantity < quantity) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
        }
        this.stockQuantity -= quantity;
    }

    public void increaseStock(int quantity) {
        validatePositive((long) quantity, "증가 수량은 0보다 커야 합니다.");
        this.stockQuantity += quantity;
    }

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    public void validateOrderQuantity(int quantity) {
        if (quantity > this.maxOrderQuantity) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "상품 '" + this.name + "'의 최대 주문 수량(" + this.maxOrderQuantity + ")을 초과했습니다.");
        }
    }

    private static void validateNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, message);
        }
    }

    private static void validatePositive(Long value, String message) {
        if (value == null || value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, message);
        }
    }

    private static void validateNotNegative(int value, String message) {
        if (value < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, message);
        }
    }

    public Long getBrandId() {
        return brandId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Long getPrice() {
        return price;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public int getMaxOrderQuantity() {
        return maxOrderQuantity;
    }

    public int getLikeCount() {
        return likeCount;
    }
}

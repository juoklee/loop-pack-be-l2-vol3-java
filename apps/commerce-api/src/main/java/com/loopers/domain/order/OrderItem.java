package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_item")
public class OrderItem extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "product_price", nullable = false)
    private Long productPrice;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    protected OrderItem() {}

    private OrderItem(Long orderId, Long productId, String productName, Long productPrice, int quantity) {
        this.orderId = orderId;
        this.productId = productId;
        this.productName = productName;
        this.productPrice = productPrice;
        this.quantity = quantity;
    }

    public static OrderItem create(Long orderId, Long productId, String productName,
                                   Long productPrice, int quantity) {
        validateNotNull(orderId, "주문 ID는 필수입니다.");
        validateNotNull(productId, "상품 ID는 필수입니다.");
        validateNotBlank(productName, "상품명은 필수입니다.");
        validatePositive(productPrice, "상품 가격은 0보다 커야 합니다.");
        validatePositiveInt(quantity, "주문 수량은 0보다 커야 합니다.");
        return new OrderItem(orderId, productId, productName, productPrice, quantity);
    }

    public Long getSubtotal() {
        return productPrice * quantity;
    }

    private static void validateNotNull(Object value, String message) {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, message);
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

    private static void validatePositiveInt(int value, String message) {
        if (value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, message);
        }
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public Long getProductPrice() {
        return productPrice;
    }

    public int getQuantity() {
        return quantity;
    }
}

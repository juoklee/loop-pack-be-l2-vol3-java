package com.loopers.domain.brand;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "brand")
public class Brand extends BaseEntity {

    @Column(unique = true, nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private int likeCount;

    protected Brand() {}

    private Brand(String name, String description) {
        this.name = name;
        this.description = description;
        this.likeCount = 0;
    }

    public static Brand create(String name, String description) {
        validateNotBlank(name, "브랜드명은 필수입니다.");
        return new Brand(name, description);
    }

    public void update(String name, String description) {
        validateNotBlank(name, "브랜드명은 필수입니다.");
        this.name = name;
        this.description = description;
    }

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    private static void validateNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, message);
        }
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getLikeCount() {
        return likeCount;
    }
}

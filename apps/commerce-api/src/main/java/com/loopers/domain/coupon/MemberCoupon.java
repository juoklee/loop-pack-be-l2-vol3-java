package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

@Entity
@Table(name = "member_coupon", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"member_id", "coupon_id"})
}, indexes = {
    @Index(name = "idx_membercoupon_member", columnList = "member_id")
})
public class MemberCoupon extends BaseEntity {

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponStatus status;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Version
    private Long version;

    protected MemberCoupon() {}

    private MemberCoupon(Long memberId, Long couponId, LocalDateTime expiredAt) {
        this.memberId = memberId;
        this.couponId = couponId;
        this.expiredAt = expiredAt;
        this.status = CouponStatus.AVAILABLE;
    }

    public static MemberCoupon create(Long memberId, Long couponId, LocalDateTime expiredAt) {
        validateNotNull(memberId, "회원 ID는 필수입니다.");
        validateNotNull(couponId, "쿠폰 ID는 필수입니다.");
        validateNotNull(expiredAt, "만료일은 필수입니다.");
        return new MemberCoupon(memberId, couponId, expiredAt);
    }

    public void use() {
        if (isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰은 사용할 수 없습니다.");
        }
        if (this.status != CouponStatus.AVAILABLE) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용 가능한 상태의 쿠폰만 사용할 수 있습니다.");
        }
        this.status = CouponStatus.USED;
        this.usedAt = LocalDateTime.now();
    }

    public void expire() {
        if (this.status == CouponStatus.USED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 사용된 쿠폰은 만료 처리할 수 없습니다.");
        }
        this.status = CouponStatus.EXPIRED;
    }

    public void restore() {
        if (this.status != CouponStatus.USED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용된 쿠폰만 복원할 수 있습니다.");
        }
        this.status = CouponStatus.AVAILABLE;
        this.usedAt = null;
    }

    public boolean isUsable() {
        return this.status == CouponStatus.AVAILABLE && !isExpired();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiredAt);
    }

    public void validateOwner(Long memberId) {
        if (!this.memberId.equals(memberId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰의 소유자가 아닙니다.");
        }
    }

    private static void validateNotNull(Object value, String message) {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, message);
        }
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getCouponId() {
        return couponId;
    }

    public LocalDateTime getExpiredAt() {
        return expiredAt;
    }

    public CouponStatus getStatus() {
        return status;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    public Long getVersion() {
        return version;
    }
}

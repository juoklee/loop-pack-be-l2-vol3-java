package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "coupon_issue_request", indexes = {
    @Index(name = "idx_member_coupon", columnList = "member_id, coupon_id")
})
public class CouponIssueRequest extends BaseEntity {

    @Column(name = "request_id", nullable = false, unique = true, length = 36)
    private String requestId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CouponIssueRequestStatus status;

    @Column(name = "fail_reason")
    private String failReason;

    @Column(name = "member_coupon_id")
    private Long memberCouponId;

    protected CouponIssueRequest() {}

    public static CouponIssueRequest create(String requestId, Long memberId, Long couponId) {
        CouponIssueRequest request = new CouponIssueRequest();
        request.requestId = requestId;
        request.memberId = memberId;
        request.couponId = couponId;
        request.status = CouponIssueRequestStatus.PENDING;
        return request;
    }

    public void complete(Long memberCouponId) {
        this.status = CouponIssueRequestStatus.COMPLETED;
        this.memberCouponId = memberCouponId;
    }

    public void fail(String failReason) {
        this.status = CouponIssueRequestStatus.FAILED;
        this.failReason = failReason;
    }

    public boolean isProcessed() {
        return this.status != CouponIssueRequestStatus.PENDING;
    }

    public String getRequestId() {
        return requestId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getCouponId() {
        return couponId;
    }

    public CouponIssueRequestStatus getStatus() {
        return status;
    }

    public String getFailReason() {
        return failReason;
    }

    public Long getMemberCouponId() {
        return memberCouponId;
    }
}

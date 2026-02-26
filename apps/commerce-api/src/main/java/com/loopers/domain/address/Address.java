package com.loopers.domain.address;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "address")
public class Address extends BaseEntity {

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private String recipientName;

    @Column(nullable = false)
    private String recipientPhone;

    @Column(nullable = false)
    private String zipCode;

    @Column(nullable = false)
    private String address1;

    private String address2;

    @Column(nullable = false)
    private boolean isDefault;

    protected Address() {}

    private Address(Long memberId, String label, String recipientName, String recipientPhone,
                    String zipCode, String address1, String address2, boolean isDefault) {
        this.memberId = memberId;
        this.label = label;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.zipCode = zipCode;
        this.address1 = address1;
        this.address2 = address2;
        this.isDefault = isDefault;
    }

    public static Address create(Long memberId, String label, String recipientName, String recipientPhone,
                                  String zipCode, String address1, String address2, boolean isDefault) {
        validateNotBlank(label, "배송지명은 필수입니다.");
        validateNotBlank(recipientName, "수령인 이름은 필수입니다.");
        validateNotBlank(recipientPhone, "수령인 전화번호는 필수입니다.");
        validateNotBlank(zipCode, "우편번호는 필수입니다.");
        validateNotBlank(address1, "기본주소는 필수입니다.");
        return new Address(memberId, label, recipientName, recipientPhone, zipCode, address1, address2, isDefault);
    }

    public void update(String label, String recipientName, String recipientPhone,
                       String zipCode, String address1, String address2) {
        validateNotBlank(label, "배송지명은 필수입니다.");
        validateNotBlank(recipientName, "수령인 이름은 필수입니다.");
        validateNotBlank(recipientPhone, "수령인 전화번호는 필수입니다.");
        validateNotBlank(zipCode, "우편번호는 필수입니다.");
        validateNotBlank(address1, "기본주소는 필수입니다.");
        this.label = label;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.zipCode = zipCode;
        this.address1 = address1;
        this.address2 = address2;
    }

    @Override
    public void delete() {
        if (this.isDefault) {
            throw new CoreException(ErrorType.BAD_REQUEST, "기본 배송지는 삭제할 수 없습니다. 다른 배송지를 기본으로 변경 후 삭제해 주세요.");
        }
        super.delete();
    }

    public void changeDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    private static void validateNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, message);
        }
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getLabel() {
        return label;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getRecipientPhone() {
        return recipientPhone;
    }

    public String getZipCode() {
        return zipCode;
    }

    public String getAddress1() {
        return address1;
    }

    public String getAddress2() {
        return address2;
    }

    public boolean getIsDefault() {
        return isDefault;
    }
}

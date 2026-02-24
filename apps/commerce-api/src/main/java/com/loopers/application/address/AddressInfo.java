package com.loopers.application.address;

import com.loopers.domain.address.Address;

public record AddressInfo(Long id, String label, String recipientName, String recipientPhone,
                          String zipCode, String address1, String address2, boolean isDefault) {

    public static AddressInfo from(Address address) {
        return new AddressInfo(
            address.getId(),
            address.getLabel(),
            address.getRecipientName(),
            address.getRecipientPhone(),
            address.getZipCode(),
            address.getAddress1(),
            address.getAddress2(),
            address.getIsDefault()
        );
    }
}

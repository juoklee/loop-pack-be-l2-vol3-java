package com.loopers.interfaces.api.address;

import com.loopers.application.address.AddressInfo;

import java.util.List;

public class AddressV1Dto {

    public record CreateAddressRequest(
        String label,
        String recipientName,
        String recipientPhone,
        String zipCode,
        String address1,
        String address2
    ) {}

    public record UpdateAddressRequest(
        String label,
        String recipientName,
        String recipientPhone,
        String zipCode,
        String address1,
        String address2
    ) {}

    public record AddressResponse(
        AddressDto address
    ) {
        public static AddressResponse from(AddressInfo info) {
            return new AddressResponse(AddressDto.from(info));
        }
    }

    public record AddressListResponse(
        List<AddressDto> addresses
    ) {
        public static AddressListResponse from(List<AddressInfo> infos) {
            return new AddressListResponse(
                infos.stream().map(AddressDto::from).toList()
            );
        }
    }

    public record AddressDto(
        Long id,
        String label,
        String recipientName,
        String recipientPhone,
        String zipCode,
        String address1,
        String address2,
        boolean isDefault
    ) {
        public static AddressDto from(AddressInfo info) {
            return new AddressDto(
                info.id(),
                info.label(),
                info.recipientName(),
                info.recipientPhone(),
                info.zipCode(),
                info.address1(),
                info.address2(),
                info.isDefault()
            );
        }
    }
}

package com.loopers.application.address;

import com.loopers.domain.address.Address;
import com.loopers.domain.address.AddressService;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class AddressFacade {

    private final AddressService addressService;
    private final MemberService memberService;

    public AddressInfo register(String loginId, String label, String recipientName, String recipientPhone,
                                String zipCode, String address1, String address2) {
        Long memberId = getMemberId(loginId);
        Address address = addressService.register(memberId, label, recipientName, recipientPhone,
            zipCode, address1, address2);
        return AddressInfo.from(address);
    }

    public AddressInfo getAddress(String loginId, Long addressId) {
        Long memberId = getMemberId(loginId);
        Address address = addressService.getAddress(addressId, memberId);
        return AddressInfo.from(address);
    }

    public List<AddressInfo> getAddresses(String loginId) {
        Long memberId = getMemberId(loginId);
        return addressService.getAddresses(memberId).stream()
            .map(AddressInfo::from)
            .toList();
    }

    public void update(String loginId, Long addressId, String label, String recipientName, String recipientPhone,
                       String zipCode, String address1, String address2) {
        Long memberId = getMemberId(loginId);
        addressService.update(addressId, memberId, label, recipientName, recipientPhone,
            zipCode, address1, address2);
    }

    public void delete(String loginId, Long addressId) {
        Long memberId = getMemberId(loginId);
        addressService.delete(addressId, memberId);
    }

    public void setDefault(String loginId, Long addressId) {
        Long memberId = getMemberId(loginId);
        addressService.setDefault(addressId, memberId);
    }

    private Long getMemberId(String loginId) {
        Member member = memberService.getMemberByLoginId(loginId);
        return member.getId();
    }
}

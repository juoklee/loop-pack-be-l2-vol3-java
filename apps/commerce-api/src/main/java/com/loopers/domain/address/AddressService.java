package com.loopers.domain.address;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class AddressService {

    private static final int MAX_ADDRESS_COUNT = 10;

    private final AddressReader addressReader;
    private final AddressRepository addressRepository;

    @Transactional
    public Address register(Long memberId, String label, String recipientName, String recipientPhone,
                            String zipCode, String address1, String address2) {
        long count = addressReader.countByMemberId(memberId);
        if (count >= MAX_ADDRESS_COUNT) {
            throw new CoreException(ErrorType.BAD_REQUEST, "배송지는 최대 " + MAX_ADDRESS_COUNT + "개까지 등록할 수 있습니다.");
        }
        boolean isDefault = count == 0;
        Address address = Address.create(memberId, label, recipientName, recipientPhone,
            zipCode, address1, address2, isDefault);
        return addressRepository.save(address);
    }

    @Transactional(readOnly = true)
    public Address getAddress(Long id, Long memberId) {
        return addressReader.findByIdAndMemberId(id, memberId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "배송지를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<Address> getAddresses(Long memberId) {
        return addressReader.findAllByMemberId(memberId);
    }

    @Transactional
    public void update(Long id, Long memberId, String label, String recipientName, String recipientPhone,
                       String zipCode, String address1, String address2) {
        Address address = getAddress(id, memberId);
        address.update(label, recipientName, recipientPhone, zipCode, address1, address2);
    }

    @Transactional
    public void delete(Long id, Long memberId) {
        Address address = getAddress(id, memberId);
        if (address.getIsDefault()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "기본 배송지는 삭제할 수 없습니다. 다른 배송지를 기본으로 변경 후 삭제해 주세요.");
        }
        address.delete();
    }

    @Transactional
    public void setDefault(Long id, Long memberId) {
        Address newDefault = getAddress(id, memberId);
        List<Address> addresses = addressReader.findAllByMemberId(memberId);
        for (Address address : addresses) {
            if (address.getIsDefault()) {
                address.setDefault(false);
            }
        }
        newDefault.setDefault(true);
    }
}

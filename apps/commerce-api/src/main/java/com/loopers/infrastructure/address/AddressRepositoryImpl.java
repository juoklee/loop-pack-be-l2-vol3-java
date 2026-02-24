package com.loopers.infrastructure.address;

import com.loopers.domain.address.Address;
import com.loopers.domain.address.AddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class AddressRepositoryImpl implements AddressRepository {

    private final AddressJpaRepository addressJpaRepository;

    @Override
    public Address save(Address address) {
        return addressJpaRepository.save(address);
    }
}

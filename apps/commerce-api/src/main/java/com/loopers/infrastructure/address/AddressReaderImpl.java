package com.loopers.infrastructure.address;

import com.loopers.domain.address.Address;
import com.loopers.domain.address.AddressReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class AddressReaderImpl implements AddressReader {

    private final AddressJpaRepository addressJpaRepository;

    @Override
    public Optional<Address> findByIdAndMemberId(Long id, Long memberId) {
        return addressJpaRepository.findByIdAndMemberIdAndDeletedAtIsNull(id, memberId);
    }

    @Override
    public List<Address> findAllByMemberId(Long memberId) {
        return addressJpaRepository.findAllByMemberIdAndDeletedAtIsNull(memberId);
    }

    @Override
    public long countByMemberId(Long memberId) {
        return addressJpaRepository.countByMemberIdAndDeletedAtIsNull(memberId);
    }
}

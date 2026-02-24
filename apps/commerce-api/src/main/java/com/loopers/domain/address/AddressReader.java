package com.loopers.domain.address;

import java.util.List;
import java.util.Optional;

public interface AddressReader {

    Optional<Address> findByIdAndMemberId(Long id, Long memberId);

    List<Address> findAllByMemberId(Long memberId);

    long countByMemberId(Long memberId);
}

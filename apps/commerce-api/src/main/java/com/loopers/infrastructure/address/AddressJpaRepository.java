package com.loopers.infrastructure.address;

import com.loopers.domain.address.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AddressJpaRepository extends JpaRepository<Address, Long> {

    Optional<Address> findByIdAndMemberIdAndDeletedAtIsNull(Long id, Long memberId);

    List<Address> findAllByMemberIdAndDeletedAtIsNull(Long memberId);

    long countByMemberIdAndDeletedAtIsNull(Long memberId);
}

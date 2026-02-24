package com.loopers.domain.address;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AddressServiceTest {

    private AddressService addressService;
    private FakeAddressReader fakeAddressReader;
    private FakeAddressRepository fakeAddressRepository;

    @BeforeEach
    void setUp() {
        fakeAddressReader = new FakeAddressReader();
        fakeAddressRepository = new FakeAddressRepository(fakeAddressReader);
        addressService = new AddressService(fakeAddressReader, fakeAddressRepository);
    }

    @DisplayName("배송지를 등록할 때, ")
    @Nested
    class Register {

        @DisplayName("첫 등록이면, 자동으로 기본 배송지가 된다.")
        @Test
        void setsDefault_whenFirstAddress() {
            // Act
            Address address = addressService.register(1L, "집", "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", "101동 202호");

            // Assert
            assertAll(
                () -> assertThat(address.getLabel()).isEqualTo("집"),
                () -> assertThat(address.getIsDefault()).isTrue()
            );
        }

        @DisplayName("이미 배송지가 있으면, 기본 배송지가 아닌 상태로 등록된다.")
        @Test
        void doesNotSetDefault_whenOtherAddressesExist() {
            // Arrange
            addressService.register(1L, "집", "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", "101동 202호");

            // Act
            Address address = addressService.register(1L, "회사", "홍길동", "010-1234-5678",
                "54321", "서울시 서초구", null);

            // Assert
            assertThat(address.getIsDefault()).isFalse();
        }

        @DisplayName("최대 10개를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenExceedsMaxCount() {
            // Arrange
            for (int i = 0; i < 10; i++) {
                addressService.register(1L, "배송지" + i, "홍길동", "010-1234-5678",
                    "12345", "주소" + i, null);
            }

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                addressService.register(1L, "배송지11", "홍길동", "010-1234-5678",
                    "12345", "주소11", null)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("배송지를 조회할 때, ")
    @Nested
    class GetAddress {

        @DisplayName("존재하는 배송지이면, 배송지를 반환한다.")
        @Test
        void returnsAddress_whenExists() {
            // Arrange
            Address saved = addressService.register(1L, "집", "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", null);

            // Act
            Address found = addressService.getAddress(1L, 1L);

            // Assert
            assertThat(found.getLabel()).isEqualTo("집");
        }

        @DisplayName("존재하지 않는 배송지이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotExists() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                addressService.getAddress(999L, 1L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("타인의 배송지이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOtherMember() {
            // Arrange
            addressService.register(1L, "집", "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", null);

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                addressService.getAddress(1L, 2L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("배송지 목록을 조회할 때, ")
    @Nested
    class GetAddresses {

        @DisplayName("본인의 배송지만 반환한다.")
        @Test
        void returnsOwnAddresses() {
            // Arrange
            addressService.register(1L, "집", "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", null);
            addressService.register(1L, "회사", "홍길동", "010-1234-5678",
                "54321", "서울시 서초구", null);
            addressService.register(2L, "타인집", "김철수", "010-9999-9999",
                "99999", "부산시", null);

            // Act
            List<Address> addresses = addressService.getAddresses(1L);

            // Assert
            assertThat(addresses).hasSize(2);
        }
    }

    @DisplayName("배송지를 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("유효한 정보로 수정하면, 정상적으로 수정된다.")
        @Test
        void updatesAddress_whenValid() {
            // Arrange
            addressService.register(1L, "집", "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", null);

            // Act
            addressService.update(1L, 1L, "회사", "김철수", "010-9876-5432",
                "54321", "서울시 서초구", "301동");

            // Assert
            Address updated = addressService.getAddress(1L, 1L);
            assertAll(
                () -> assertThat(updated.getLabel()).isEqualTo("회사"),
                () -> assertThat(updated.getRecipientName()).isEqualTo("김철수"),
                () -> assertThat(updated.getAddress1()).isEqualTo("서울시 서초구")
            );
        }

        @DisplayName("존재하지 않는 배송지이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotExists() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                addressService.update(999L, 1L, "회사", "김철수", "010-9876-5432",
                    "54321", "서울시 서초구", null)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("배송지를 삭제할 때, ")
    @Nested
    class Delete {

        @DisplayName("비기본 배송지이면, soft delete 처리된다.")
        @Test
        void deletesAddress_whenNotDefault() {
            // Arrange
            addressService.register(1L, "집", "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", null);
            addressService.register(1L, "회사", "홍길동", "010-1234-5678",
                "54321", "서울시 서초구", null);

            // Act
            addressService.delete(2L, 1L);

            // Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                addressService.getAddress(2L, 1L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("기본 배송지이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDefaultAddress() {
            // Arrange
            addressService.register(1L, "집", "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", null); // 첫 등록 → 기본 배송지

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                addressService.delete(1L, 1L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 배송지이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotExists() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                addressService.delete(999L, 1L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("기본 배송지를 설정할 때, ")
    @Nested
    class SetDefault {

        @DisplayName("다른 배송지를 기본으로 설정하면, 기존 기본이 해제되고 새 기본이 설정된다.")
        @Test
        void changesDefault_whenSettingNewDefault() {
            // Arrange
            addressService.register(1L, "집", "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", null); // id=1, 기본
            addressService.register(1L, "회사", "홍길동", "010-1234-5678",
                "54321", "서울시 서초구", null); // id=2, 비기본

            // Act
            addressService.setDefault(2L, 1L);

            // Assert
            Address oldDefault = addressService.getAddress(1L, 1L);
            Address newDefault = addressService.getAddress(2L, 1L);
            assertAll(
                () -> assertThat(oldDefault.getIsDefault()).isFalse(),
                () -> assertThat(newDefault.getIsDefault()).isTrue()
            );
        }

        @DisplayName("존재하지 않는 배송지이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotExists() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                addressService.setDefault(999L, 1L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    // Fake 구현체
    static class FakeAddressReader implements AddressReader {
        private final Map<Long, Address> addresses = new HashMap<>();
        private final Map<Long, List<Address>> addressesByMemberId = new HashMap<>();

        void addAddress(Long id, Long memberId, Address address) {
            addresses.put(id, address);
            addressesByMemberId.computeIfAbsent(memberId, k -> new ArrayList<>()).add(address);
        }

        void removeAddress(Long id, Long memberId) {
            addresses.remove(id);
            List<Address> list = addressesByMemberId.get(memberId);
            if (list != null) {
                list.removeIf(a -> a == addresses.get(id));
            }
        }

        @Override
        public Optional<Address> findByIdAndMemberId(Long id, Long memberId) {
            Address address = addresses.get(id);
            if (address != null && address.getMemberId().equals(memberId) && address.getDeletedAt() == null) {
                return Optional.of(address);
            }
            return Optional.empty();
        }

        @Override
        public List<Address> findAllByMemberId(Long memberId) {
            return addressesByMemberId.getOrDefault(memberId, List.of()).stream()
                .filter(a -> a.getDeletedAt() == null)
                .toList();
        }

        @Override
        public long countByMemberId(Long memberId) {
            return findAllByMemberId(memberId).size();
        }
    }

    static class FakeAddressRepository implements AddressRepository {
        private final FakeAddressReader fakeAddressReader;
        private long idSequence = 1L;

        FakeAddressRepository(FakeAddressReader fakeAddressReader) {
            this.fakeAddressReader = fakeAddressReader;
        }

        @Override
        public Address save(Address address) {
            long id = idSequence++;
            fakeAddressReader.addAddress(id, address.getMemberId(), address);
            return address;
        }
    }
}

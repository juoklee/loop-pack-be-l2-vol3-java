package com.loopers.domain.coupon;

import com.loopers.domain.PageResult;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponServiceTest {

    private CouponService couponService;
    private FakeCouponReader fakeCouponReader;
    private FakeCouponRepository fakeCouponRepository;
    private FakeMemberCouponReader fakeMemberCouponReader;
    private FakeMemberCouponRepository fakeMemberCouponRepository;

    private static final LocalDateTime FUTURE = LocalDateTime.now().plusDays(30);
    private static final LocalDateTime PAST = LocalDateTime.now().minusDays(1);

    @BeforeEach
    void setUp() {
        fakeCouponReader = new FakeCouponReader();
        fakeCouponRepository = new FakeCouponRepository(fakeCouponReader);
        fakeMemberCouponReader = new FakeMemberCouponReader();
        fakeMemberCouponRepository = new FakeMemberCouponRepository(fakeMemberCouponReader);
        couponService = new CouponService(
            fakeCouponRepository, fakeCouponReader,
            fakeMemberCouponRepository, fakeMemberCouponReader
        );
    }

    @DisplayName("쿠폰 템플릿을 생성할 때, ")
    @Nested
    class CreateCoupon {

        @DisplayName("유효한 정보면, 쿠폰이 저장된다.")
        @Test
        void savesCoupon_whenFieldsAreValid() {
            // Act
            Coupon coupon = couponService.createCoupon("신규가입 쿠폰", CouponType.FIXED, 5000L, null, FUTURE);

            // Assert
            assertAll(
                () -> assertThat(coupon.getName()).isEqualTo("신규가입 쿠폰"),
                () -> assertThat(coupon.getType()).isEqualTo(CouponType.FIXED),
                () -> assertThat(coupon.getValue()).isEqualTo(5000L)
            );
        }
    }

    @DisplayName("쿠폰 템플릿을 조회할 때, ")
    @Nested
    class GetCoupon {

        @DisplayName("존재하는 쿠폰이면, 쿠폰을 반환한다.")
        @Test
        void returnsCoupon_whenCouponExists() {
            // Arrange
            Coupon coupon = couponService.createCoupon("쿠폰", CouponType.FIXED, 5000L, null, FUTURE);

            // Act
            Coupon found = couponService.getCoupon(1L);

            // Assert
            assertThat(found.getName()).isEqualTo("쿠폰");
        }

        @DisplayName("존재하지 않는 쿠폰이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponNotExists() {
            CoreException exception = assertThrows(CoreException.class, () ->
                couponService.getCoupon(999L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("쿠폰 목록을 조회할 때, ")
    @Nested
    class GetCoupons {

        @DisplayName("쿠폰이 존재하면, 페이징된 결과를 반환한다.")
        @Test
        void returnsPagedResult_whenCouponsExist() {
            // Arrange
            couponService.createCoupon("쿠폰1", CouponType.FIXED, 5000L, null, FUTURE);
            couponService.createCoupon("쿠폰2", CouponType.RATE, 10L, null, FUTURE);

            // Act
            PageResult<Coupon> result = couponService.getCoupons(0, 20);

            // Assert
            assertAll(
                () -> assertThat(result.content()).hasSize(2),
                () -> assertThat(result.page()).isZero(),
                () -> assertThat(result.size()).isEqualTo(20)
            );
        }
    }

    @DisplayName("쿠폰 템플릿을 수정할 때, ")
    @Nested
    class UpdateCoupon {

        @DisplayName("유효한 정보로 수정하면, 쿠폰이 수정된다.")
        @Test
        void updatesCoupon_whenFieldsAreValid() {
            // Arrange
            couponService.createCoupon("기존 쿠폰", CouponType.FIXED, 5000L, null, FUTURE);
            LocalDateTime newExpiredAt = FUTURE.plusDays(10);

            // Act
            couponService.updateCoupon(1L, "수정된 쿠폰", 3000L, 20000L, newExpiredAt, null);

            // Assert
            Coupon updated = couponService.getCoupon(1L);
            assertAll(
                () -> assertThat(updated.getName()).isEqualTo("수정된 쿠폰"),
                () -> assertThat(updated.getValue()).isEqualTo(3000L),
                () -> assertThat(updated.getMinOrderAmount()).isEqualTo(20000L)
            );
        }

        @DisplayName("존재하지 않는 쿠폰이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponNotExists() {
            CoreException exception = assertThrows(CoreException.class, () ->
                couponService.updateCoupon(999L, "이름", 3000L, null, FUTURE, null)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("쿠폰 템플릿을 삭제할 때, ")
    @Nested
    class DeleteCoupon {

        @DisplayName("존재하는 쿠폰이면, soft delete 처리된다.")
        @Test
        void deletesCoupon_whenCouponExists() {
            // Arrange
            Coupon coupon = couponService.createCoupon("쿠폰", CouponType.FIXED, 5000L, null, FUTURE);

            // Act
            couponService.deleteCoupon(1L);

            // Assert
            assertThat(coupon.getDeletedAt()).isNotNull();
        }

        @DisplayName("존재하지 않는 쿠폰이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponNotExists() {
            CoreException exception = assertThrows(CoreException.class, () ->
                couponService.deleteCoupon(999L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("쿠폰을 발급할 때, ")
    @Nested
    class IssueCoupon {

        @DisplayName("유효한 쿠폰이면, MemberCoupon이 생성된다.")
        @Test
        void createsMemberCoupon_whenCouponIsValid() {
            // Arrange
            couponService.createCoupon("쿠폰", CouponType.FIXED, 5000L, null, FUTURE);

            // Act
            MemberCoupon memberCoupon = couponService.issueCoupon(1L, 100L);

            // Assert
            assertAll(
                () -> assertThat(memberCoupon.getMemberId()).isEqualTo(100L),
                () -> assertThat(memberCoupon.getCouponId()).isEqualTo(1L),
                () -> assertThat(memberCoupon.getStatus()).isEqualTo(CouponStatus.AVAILABLE)
            );
        }

        @DisplayName("존재하지 않는 쿠폰이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponNotExists() {
            CoreException exception = assertThrows(CoreException.class, () ->
                couponService.issueCoupon(999L, 100L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("만료된 쿠폰이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCouponIsExpired() {
            // Arrange
            couponService.createCoupon("만료 쿠폰", CouponType.FIXED, 5000L, null, PAST);

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                couponService.issueCoupon(1L, 100L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이미 발급받은 쿠폰이면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenAlreadyIssued() {
            // Arrange
            couponService.createCoupon("쿠폰", CouponType.FIXED, 5000L, null, FUTURE);
            couponService.issueCoupon(1L, 100L);

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                couponService.issueCoupon(1L, 100L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("validDays 쿠폰이면, 발급 시점 + validDays로 개인 만료일이 설정된다.")
        @Test
        void setsMemberExpiredAt_whenValidDaysIsSet() {
            // Arrange — validDays=7인 쿠폰
            couponService.createCoupon("기간제 쿠폰", CouponType.FIXED, 5000L, null, FUTURE, 7, null);

            // Act
            MemberCoupon mc = couponService.issueCoupon(1L, 100L);

            // Assert — 개인 만료일이 쿠폰 expiredAt이 아닌 발급시점+7일 근처
            LocalDateTime expectedAround = LocalDateTime.now().plusDays(7);
            assertThat(mc.getExpiredAt()).isBefore(FUTURE); // 쿠폰 만료일보다 이전
            assertThat(mc.getExpiredAt()).isAfter(expectedAround.minusMinutes(1));
            assertThat(mc.getExpiredAt()).isBefore(expectedAround.plusMinutes(1));
        }

        @DisplayName("validDays가 없으면, 쿠폰 만료일이 개인 만료일로 설정된다.")
        @Test
        void setsCouponExpiredAt_whenValidDaysIsNull() {
            // Arrange
            couponService.createCoupon("일반 쿠폰", CouponType.FIXED, 5000L, null, FUTURE);

            // Act
            MemberCoupon mc = couponService.issueCoupon(1L, 100L);

            // Assert
            assertThat(mc.getExpiredAt()).isEqualTo(FUTURE);
        }

        @DisplayName("수량 제한 쿠폰의 수량이 소진되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityExhausted() {
            // Arrange - 수량 1개짜리 쿠폰
            couponService.createCoupon("한정 쿠폰", CouponType.FIXED, 5000L, null, FUTURE, 1);
            couponService.issueCoupon(1L, 100L); // 1/1 소진

            // Act & Assert - 다른 사용자가 발급 시도
            CoreException exception = assertThrows(CoreException.class, () ->
                couponService.issueCoupon(1L, 200L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("사용자 쿠폰 목록을 조회할 때, ")
    @Nested
    class GetMemberCoupons {

        @DisplayName("발급된 쿠폰이 있으면, 페이징된 결과를 반환한다.")
        @Test
        void returnsPagedResult_whenMemberCouponsExist() {
            // Arrange
            couponService.createCoupon("쿠폰1", CouponType.FIXED, 5000L, null, FUTURE);
            couponService.createCoupon("쿠폰2", CouponType.RATE, 10L, null, FUTURE);
            couponService.issueCoupon(1L, 100L);
            couponService.issueCoupon(2L, 100L);

            // Act
            PageResult<MemberCoupon> result = couponService.getMemberCoupons(100L, 0, 20);

            // Assert
            assertAll(
                () -> assertThat(result.content()).hasSize(2),
                () -> assertThat(result.page()).isZero()
            );
        }
    }

    @DisplayName("쿠폰 발급 내역을 조회할 때, ")
    @Nested
    class GetIssuedCoupons {

        @DisplayName("발급 내역이 있으면, 페이징된 결과를 반환한다.")
        @Test
        void returnsPagedResult_whenIssuedCouponsExist() {
            // Arrange
            couponService.createCoupon("쿠폰", CouponType.FIXED, 5000L, null, FUTURE);
            couponService.issueCoupon(1L, 100L);
            couponService.issueCoupon(1L, 200L);

            // Act
            PageResult<MemberCoupon> result = couponService.getIssuedCoupons(1L, 0, 20);

            // Assert
            assertThat(result.content()).hasSize(2);
        }
    }

    @DisplayName("쿠폰을 사용할 때, ")
    @Nested
    class UseCoupon {

        @DisplayName("유효한 쿠폰이면, 할인액을 반환하고 USED 상태로 변경된다.")
        @Test
        void returnsCouponApplyResult_whenValid() {
            // Arrange
            couponService.createCoupon("5000원 할인", CouponType.FIXED, 5000L, null, FUTURE);
            MemberCoupon mc = couponService.issueCoupon(1L, 100L);

            // Act
            CouponService.CouponApplyResult result = couponService.useCoupon(1L, 100L, 50000L);

            // Assert
            assertAll(
                () -> assertThat(result.discountAmount()).isEqualTo(5000L),
                () -> assertThat(mc.getStatus()).isEqualTo(CouponStatus.USED),
                () -> assertThat(mc.getUsedAt()).isNotNull()
            );
        }

        @DisplayName("정률 쿠폰이면, 주문 금액 기준으로 할인액을 계산한다.")
        @Test
        void calculatesRateDiscount_whenRateCoupon() {
            // Arrange
            couponService.createCoupon("10% 할인", CouponType.RATE, 10L, null, FUTURE);
            couponService.issueCoupon(1L, 100L);

            // Act
            CouponService.CouponApplyResult result = couponService.useCoupon(1L, 100L, 80000L);

            // Assert
            assertThat(result.discountAmount()).isEqualTo(8000L);
        }

        @DisplayName("존재하지 않는 memberCouponId이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenMemberCouponNotExists() {
            CoreException exception = assertThrows(CoreException.class, () ->
                couponService.useCoupon(999L, 100L, 50000L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("다른 사용자의 쿠폰이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNotOwner() {
            // Arrange
            couponService.createCoupon("쿠폰", CouponType.FIXED, 5000L, null, FUTURE);
            couponService.issueCoupon(1L, 100L);

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                couponService.useCoupon(1L, 200L, 50000L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("MemberCoupon이 만료되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenMemberCouponExpired() {
            // Arrange — 쿠폰 자체는 유효하지만 개인 만료일이 지난 상태
            Coupon coupon = Coupon.create("쿠폰", CouponType.FIXED, 5000L, null, FUTURE);
            fakeCouponReader.addCoupon(1L, coupon);
            MemberCoupon mc = MemberCoupon.create(100L, 1L, PAST); // 개인 만료일이 과거
            fakeMemberCouponReader.addMemberCoupon(1L, mc);

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                couponService.useCoupon(1L, 100L, 50000L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이미 사용된 쿠폰이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAlreadyUsed() {
            // Arrange
            couponService.createCoupon("쿠폰", CouponType.FIXED, 5000L, null, FUTURE);
            couponService.issueCoupon(1L, 100L);
            couponService.useCoupon(1L, 100L, 50000L);

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                couponService.useCoupon(1L, 100L, 50000L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("최소 주문 금액 미달이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBelowMinOrderAmount() {
            // Arrange
            couponService.createCoupon("쿠폰", CouponType.FIXED, 5000L, 50000L, FUTURE);
            couponService.issueCoupon(1L, 100L);

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () ->
                couponService.useCoupon(1L, 100L, 30000L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("쿠폰을 복원할 때, ")
    @Nested
    class RestoreCoupon {

        @DisplayName("사용된 쿠폰이면, AVAILABLE로 복원된다.")
        @Test
        void restoresCoupon_whenUsed() {
            // Arrange
            couponService.createCoupon("쿠폰", CouponType.FIXED, 5000L, null, FUTURE);
            couponService.issueCoupon(1L, 100L);
            couponService.useCoupon(1L, 100L, 50000L);

            // Act
            couponService.restoreCoupon(1L);

            // Assert
            MemberCoupon mc = couponService.getMemberCoupon(1L);
            assertAll(
                () -> assertThat(mc.getStatus()).isEqualTo(CouponStatus.AVAILABLE),
                () -> assertThat(mc.getUsedAt()).isNull()
            );
        }

        @DisplayName("존재하지 않는 memberCouponId이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenMemberCouponNotExists() {
            CoreException exception = assertThrows(CoreException.class, () ->
                couponService.restoreCoupon(999L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    // ── Fake 구현체 ──

    static class FakeCouponReader implements CouponReader {
        private final Map<Long, Coupon> coupons = new HashMap<>();

        void addCoupon(Long id, Coupon coupon) {
            coupons.put(id, coupon);
        }

        @Override
        public Optional<Coupon> findById(Long id) {
            return Optional.ofNullable(coupons.get(id));
        }

        @Override
        public Optional<Coupon> findByIdForUpdate(Long id) {
            return Optional.ofNullable(coupons.get(id));
        }

        @Override
        public List<Coupon> findAllByIdIn(List<Long> ids) {
            return ids.stream()
                .map(coupons::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        }

        @Override
        public PageResult<Coupon> findAll(int page, int size) {
            List<Coupon> all = new ArrayList<>(coupons.values());
            return new PageResult<>(all, all.size(), 1, page, size);
        }
    }

    static class FakeCouponRepository implements CouponRepository {
        private final FakeCouponReader fakeCouponReader;
        private long idSequence = 1L;

        FakeCouponRepository(FakeCouponReader fakeCouponReader) {
            this.fakeCouponReader = fakeCouponReader;
        }

        @Override
        public Coupon save(Coupon coupon) {
            long id = idSequence++;
            fakeCouponReader.addCoupon(id, coupon);
            return coupon;
        }
    }

    static class FakeMemberCouponReader implements MemberCouponReader {
        private final Map<Long, MemberCoupon> memberCoupons = new HashMap<>();

        void addMemberCoupon(Long id, MemberCoupon memberCoupon) {
            memberCoupons.put(id, memberCoupon);
        }

        @Override
        public Optional<MemberCoupon> findById(Long id) {
            return Optional.ofNullable(memberCoupons.get(id));
        }

        @Override
        public Optional<MemberCoupon> findByMemberIdAndCouponId(Long memberId, Long couponId) {
            return memberCoupons.values().stream()
                .filter(mc -> mc.getMemberId().equals(memberId) && mc.getCouponId().equals(couponId))
                .findFirst();
        }

        @Override
        public PageResult<MemberCoupon> findAllByMemberId(Long memberId, int page, int size) {
            List<MemberCoupon> filtered = memberCoupons.values().stream()
                .filter(mc -> mc.getMemberId().equals(memberId))
                .toList();
            return new PageResult<>(filtered, filtered.size(), 1, page, size);
        }

        @Override
        public PageResult<MemberCoupon> findAllByCouponId(Long couponId, int page, int size) {
            List<MemberCoupon> filtered = memberCoupons.values().stream()
                .filter(mc -> mc.getCouponId().equals(couponId))
                .toList();
            return new PageResult<>(filtered, filtered.size(), 1, page, size);
        }
    }

    static class FakeMemberCouponRepository implements MemberCouponRepository {
        private final FakeMemberCouponReader fakeMemberCouponReader;
        private long idSequence = 1L;

        FakeMemberCouponRepository(FakeMemberCouponReader fakeMemberCouponReader) {
            this.fakeMemberCouponReader = fakeMemberCouponReader;
        }

        @Override
        public MemberCoupon save(MemberCoupon memberCoupon) {
            long id = idSequence++;
            fakeMemberCouponReader.addMemberCoupon(id, memberCoupon);
            return memberCoupon;
        }
    }
}

package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemberCouponTest {

    @DisplayName("사용자 쿠폰을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 값이면, AVAILABLE 상태로 생성된다.")
        @Test
        void createsMemberCoupon_withAvailableStatus() {
            // Arrange & Act
            MemberCoupon memberCoupon = MemberCoupon.create(1L, 100L);

            // Assert
            assertAll(
                () -> assertThat(memberCoupon.getMemberId()).isEqualTo(1L),
                () -> assertThat(memberCoupon.getCouponId()).isEqualTo(100L),
                () -> assertThat(memberCoupon.getStatus()).isEqualTo(CouponStatus.AVAILABLE),
                () -> assertThat(memberCoupon.getUsedAt()).isNull()
            );
        }

        @DisplayName("회원 ID가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenMemberIdIsNull() {
            CoreException exception = assertThrows(CoreException.class, () ->
                MemberCoupon.create(null, 100L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("쿠폰 ID가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCouponIdIsNull() {
            CoreException exception = assertThrows(CoreException.class, () ->
                MemberCoupon.create(1L, null)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("쿠폰을 사용할 때, ")
    @Nested
    class Use {

        @DisplayName("AVAILABLE 상태이면, USED로 변경되고 사용 시간이 기록된다.")
        @Test
        void changesStatusToUsed_whenAvailable() {
            // Arrange
            MemberCoupon memberCoupon = MemberCoupon.create(1L, 100L);

            // Act
            memberCoupon.use();

            // Assert
            assertAll(
                () -> assertThat(memberCoupon.getStatus()).isEqualTo(CouponStatus.USED),
                () -> assertThat(memberCoupon.getUsedAt()).isNotNull()
            );
        }

        @DisplayName("이미 USED 상태이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAlreadyUsed() {
            // Arrange
            MemberCoupon memberCoupon = MemberCoupon.create(1L, 100L);
            memberCoupon.use();

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, memberCoupon::use);
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("EXPIRED 상태이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenExpired() {
            // Arrange
            MemberCoupon memberCoupon = MemberCoupon.create(1L, 100L);
            memberCoupon.expire();

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, memberCoupon::use);
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("쿠폰을 만료시킬 때, ")
    @Nested
    class Expire {

        @DisplayName("AVAILABLE 상태이면, EXPIRED로 변경된다.")
        @Test
        void changesStatusToExpired_whenAvailable() {
            // Arrange
            MemberCoupon memberCoupon = MemberCoupon.create(1L, 100L);

            // Act
            memberCoupon.expire();

            // Assert
            assertThat(memberCoupon.getStatus()).isEqualTo(CouponStatus.EXPIRED);
        }

        @DisplayName("이미 USED 상태이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAlreadyUsed() {
            // Arrange
            MemberCoupon memberCoupon = MemberCoupon.create(1L, 100L);
            memberCoupon.use();

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, memberCoupon::expire);
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("소유자를 검증할 때, ")
    @Nested
    class ValidateOwner {

        @DisplayName("소유자가 일치하면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenOwnerMatches() {
            MemberCoupon memberCoupon = MemberCoupon.create(1L, 100L);
            memberCoupon.validateOwner(1L);
        }

        @DisplayName("소유자가 일치하지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOwnerDoesNotMatch() {
            MemberCoupon memberCoupon = MemberCoupon.create(1L, 100L);

            CoreException exception = assertThrows(CoreException.class, () ->
                memberCoupon.validateOwner(2L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("사용 가능 여부를 확인할 때, ")
    @Nested
    class IsUsable {

        @DisplayName("AVAILABLE 상태이면, true를 반환한다.")
        @Test
        void returnsTrue_whenAvailable() {
            MemberCoupon memberCoupon = MemberCoupon.create(1L, 100L);
            assertThat(memberCoupon.isUsable()).isTrue();
        }

        @DisplayName("USED 상태이면, false를 반환한다.")
        @Test
        void returnsFalse_whenUsed() {
            MemberCoupon memberCoupon = MemberCoupon.create(1L, 100L);
            memberCoupon.use();
            assertThat(memberCoupon.isUsable()).isFalse();
        }

        @DisplayName("EXPIRED 상태이면, false를 반환한다.")
        @Test
        void returnsFalse_whenExpired() {
            MemberCoupon memberCoupon = MemberCoupon.create(1L, 100L);
            memberCoupon.expire();
            assertThat(memberCoupon.isUsable()).isFalse();
        }
    }
}

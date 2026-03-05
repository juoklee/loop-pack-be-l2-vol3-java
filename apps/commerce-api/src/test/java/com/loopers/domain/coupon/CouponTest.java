package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponTest {

    private static final LocalDateTime FUTURE = LocalDateTime.now().plusDays(30);
    private static final LocalDateTime PAST = LocalDateTime.now().minusDays(1);

    @DisplayName("쿠폰을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("모든 필드가 유효하면, 정상적으로 생성된다.")
        @Test
        void createsCoupon_whenAllFieldsAreValid() {
            // Arrange & Act
            Coupon coupon = Coupon.create("신규가입 쿠폰", CouponType.FIXED, 5000L, null, FUTURE);

            // Assert
            assertAll(
                () -> assertThat(coupon.getName()).isEqualTo("신규가입 쿠폰"),
                () -> assertThat(coupon.getType()).isEqualTo(CouponType.FIXED),
                () -> assertThat(coupon.getValue()).isEqualTo(5000L),
                () -> assertThat(coupon.getMinOrderAmount()).isNull(),
                () -> assertThat(coupon.getExpiredAt()).isEqualTo(FUTURE)
            );
        }

        @DisplayName("최소 주문 금액이 설정되면, 정상적으로 생성된다.")
        @Test
        void createsCoupon_whenMinOrderAmountIsSet() {
            // Arrange & Act
            Coupon coupon = Coupon.create("10% 할인", CouponType.RATE, 10L, 30000L, FUTURE);

            // Assert
            assertAll(
                () -> assertThat(coupon.getType()).isEqualTo(CouponType.RATE),
                () -> assertThat(coupon.getValue()).isEqualTo(10L),
                () -> assertThat(coupon.getMinOrderAmount()).isEqualTo(30000L)
            );
        }

        @DisplayName("이름이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            CoreException exception = assertThrows(CoreException.class, () ->
                Coupon.create(null, CouponType.FIXED, 5000L, null, FUTURE)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            CoreException exception = assertThrows(CoreException.class, () ->
                Coupon.create("  ", CouponType.FIXED, 5000L, null, FUTURE)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("타입이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenTypeIsNull() {
            CoreException exception = assertThrows(CoreException.class, () ->
                Coupon.create("쿠폰", null, 5000L, null, FUTURE)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("할인 값이 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsZero() {
            CoreException exception = assertThrows(CoreException.class, () ->
                Coupon.create("쿠폰", CouponType.FIXED, 0L, null, FUTURE)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("할인 값이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsNegative() {
            CoreException exception = assertThrows(CoreException.class, () ->
                Coupon.create("쿠폰", CouponType.FIXED, -1000L, null, FUTURE)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("만료일이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenExpiredAtIsNull() {
            CoreException exception = assertThrows(CoreException.class, () ->
                Coupon.create("쿠폰", CouponType.FIXED, 5000L, null, null)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("정률 쿠폰의 할인율이 100을 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenRateExceeds100() {
            CoreException exception = assertThrows(CoreException.class, () ->
                Coupon.create("쿠폰", CouponType.RATE, 101L, null, FUTURE)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("정률 쿠폰의 할인율이 100이면, 정상적으로 생성된다.")
        @Test
        void createsCoupon_whenRateIs100() {
            Coupon coupon = Coupon.create("100% 할인", CouponType.RATE, 100L, null, FUTURE);
            assertThat(coupon.getValue()).isEqualTo(100L);
        }

        @DisplayName("validDays가 설정되면, 정상적으로 생성된다.")
        @Test
        void createsCoupon_whenValidDaysIsSet() {
            Coupon coupon = Coupon.create("기간제 쿠폰", CouponType.FIXED, 5000L, null, FUTURE, 7, null);

            assertAll(
                () -> assertThat(coupon.getValidDays()).isEqualTo(7),
                () -> assertThat(coupon.getTotalQuantity()).isNull()
            );
        }

        @DisplayName("validDays가 null이면, 기본 동작으로 생성된다.")
        @Test
        void createsCoupon_whenValidDaysIsNull() {
            Coupon coupon = Coupon.create("일반 쿠폰", CouponType.FIXED, 5000L, null, FUTURE);
            assertThat(coupon.getValidDays()).isNull();
        }
    }

    @DisplayName("쿠폰을 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("유효한 정보로 수정하면, 정상적으로 수정된다.")
        @Test
        void updatesCoupon_whenFieldsAreValid() {
            // Arrange
            Coupon coupon = Coupon.create("기존 쿠폰", CouponType.FIXED, 5000L, null, FUTURE);
            LocalDateTime newExpiredAt = FUTURE.plusDays(10);

            // Act
            coupon.updateInfo("수정된 쿠폰", 3000L, 20000L, newExpiredAt, 14);

            // Assert
            assertAll(
                () -> assertThat(coupon.getName()).isEqualTo("수정된 쿠폰"),
                () -> assertThat(coupon.getValue()).isEqualTo(3000L),
                () -> assertThat(coupon.getMinOrderAmount()).isEqualTo(20000L),
                () -> assertThat(coupon.getExpiredAt()).isEqualTo(newExpiredAt),
                () -> assertThat(coupon.getValidDays()).isEqualTo(14),
                () -> assertThat(coupon.getType()).isEqualTo(CouponType.FIXED) // type 변경 불가
            );
        }

        @DisplayName("이름이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            Coupon coupon = Coupon.create("쿠폰", CouponType.FIXED, 5000L, null, FUTURE);

            CoreException exception = assertThrows(CoreException.class, () ->
                coupon.updateInfo(null, 3000L, null, FUTURE, null)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("할인 값이 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsZero() {
            Coupon coupon = Coupon.create("쿠폰", CouponType.FIXED, 5000L, null, FUTURE);

            CoreException exception = assertThrows(CoreException.class, () ->
                coupon.updateInfo("쿠폰", 0L, null, FUTURE, null)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("정률 쿠폰 수정 시 할인율이 100을 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenRateUpdateExceeds100() {
            Coupon coupon = Coupon.create("쿠폰", CouponType.RATE, 10L, null, FUTURE);

            CoreException exception = assertThrows(CoreException.class, () ->
                coupon.updateInfo("쿠폰", 101L, null, FUTURE, null)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("할인 금액을 계산할 때, ")
    @Nested
    class CalculateDiscount {

        @DisplayName("정액 쿠폰이면, 할인 값 그대로 반환한다.")
        @Test
        void returnsFixedValue_whenTypeIsFixed() {
            // Arrange
            Coupon coupon = Coupon.create("5000원 할인", CouponType.FIXED, 5000L, null, FUTURE);

            // Act
            long discount = coupon.calculateDiscount(50000L);

            // Assert
            assertThat(discount).isEqualTo(5000L);
        }

        @DisplayName("정액 쿠폰의 할인 값이 주문 금액을 초과하면, 주문 금액을 반환한다.")
        @Test
        void returnsOrderAmount_whenFixedValueExceedsOrderAmount() {
            // Arrange
            Coupon coupon = Coupon.create("10000원 할인", CouponType.FIXED, 10000L, null, FUTURE);

            // Act
            long discount = coupon.calculateDiscount(8000L);

            // Assert
            assertThat(discount).isEqualTo(8000L);
        }

        @DisplayName("정률 쿠폰이면, 주문 금액의 비율만큼 반환한다.")
        @Test
        void returnsPercentage_whenTypeIsRate() {
            // Arrange
            Coupon coupon = Coupon.create("10% 할인", CouponType.RATE, 10L, null, FUTURE);

            // Act
            long discount = coupon.calculateDiscount(50000L);

            // Assert
            assertThat(discount).isEqualTo(5000L);
        }

        @DisplayName("정률 쿠폰 100%이면, 주문 금액 전액을 반환한다.")
        @Test
        void returnsFullAmount_whenRateIs100() {
            // Arrange
            Coupon coupon = Coupon.create("100% 할인", CouponType.RATE, 100L, null, FUTURE);

            // Act
            long discount = coupon.calculateDiscount(50000L);

            // Assert
            assertThat(discount).isEqualTo(50000L);
        }
    }

    @DisplayName("만료 여부를 확인할 때, ")
    @Nested
    class IsExpired {

        @DisplayName("만료일이 현재보다 미래이면, false를 반환한다.")
        @Test
        void returnsFalse_whenNotExpired() {
            Coupon coupon = Coupon.create("쿠폰", CouponType.FIXED, 5000L, null, FUTURE);
            assertThat(coupon.isExpired()).isFalse();
        }

        @DisplayName("만료일이 현재보다 과거이면, true를 반환한다.")
        @Test
        void returnsTrue_whenExpired() {
            Coupon coupon = Coupon.create("쿠폰", CouponType.FIXED, 5000L, null, PAST);
            assertThat(coupon.isExpired()).isTrue();
        }
    }

    @DisplayName("쿠폰을 발급(수량 차감)할 때, ")
    @Nested
    class IssueOne {

        @DisplayName("수량 제한이 없으면(totalQuantity == null), 발급이 성공한다.")
        @Test
        void issuesSuccessfully_whenNoQuantityLimit() {
            Coupon coupon = Coupon.create("무제한 쿠폰", CouponType.FIXED, 5000L, null, FUTURE, null);

            coupon.issueOne();

            assertThat(coupon.getIssuedQuantity()).isEqualTo(1);
        }

        @DisplayName("수량이 남아있으면, 발급이 성공하고 issuedQuantity가 증가한다.")
        @Test
        void issuesSuccessfully_whenQuantityRemains() {
            Coupon coupon = Coupon.create("한정 쿠폰", CouponType.FIXED, 5000L, null, FUTURE, 100);

            coupon.issueOne();

            assertAll(
                () -> assertThat(coupon.getIssuedQuantity()).isEqualTo(1),
                () -> assertThat(coupon.getTotalQuantity()).isEqualTo(100)
            );
        }

        @DisplayName("수량이 모두 소진되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityExhausted() {
            Coupon coupon = Coupon.create("한정 쿠폰", CouponType.FIXED, 5000L, null, FUTURE, 1);
            coupon.issueOne(); // 1/1 소진

            CoreException exception = assertThrows(CoreException.class, coupon::issueOne);
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("최소 주문 금액을 검증할 때, ")
    @Nested
    class ValidateMinOrderAmount {

        @DisplayName("최소 주문 금액 조건이 없으면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenNoMinOrderAmount() {
            Coupon coupon = Coupon.create("쿠폰", CouponType.FIXED, 5000L, null, FUTURE);
            coupon.validateMinOrderAmount(1000L);
        }

        @DisplayName("주문 금액이 최소 주문 금액 이상이면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenOrderAmountMeetsMinimum() {
            Coupon coupon = Coupon.create("쿠폰", CouponType.FIXED, 5000L, 30000L, FUTURE);
            coupon.validateMinOrderAmount(30000L);
        }

        @DisplayName("주문 금액이 최소 주문 금액 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOrderAmountBelowMinimum() {
            Coupon coupon = Coupon.create("쿠폰", CouponType.FIXED, 5000L, 30000L, FUTURE);

            CoreException exception = assertThrows(CoreException.class, () ->
                coupon.validateMinOrderAmount(29999L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}

# 01. 요구사항 명세

## 목표

상품 조회 → 좋아요 → 주문 생성까지의 이커머스 핵심 흐름 구현

---

## 액터 정의

| 액터 | 식별 방식 | 접근 범위 |
|------|-----------|-----------|
| 비회원 (Guest) | 없음 | 브랜드 목록/상세 조회, 상품 목록/상세 조회, 키워드 검색 |
| 회원 (Member) | `X-Loopers-LoginId` + `X-Loopers-LoginPw` | 좋아요, 주문, 본인 정보, 배송지 |
| 관리자 (Admin) | `X-Loopers-Ldap: loopers.admin` | 브랜드/상품 CUD, 재고 수정, 회원 조회, 주문 조회 (Brand/Product 조회는 Public API 공유) |

**액터 간 경계:**
- 회원은 **타 회원의 정보에 직접 접근할 수 없음** (좋아요 목록, 주문, 배송지 모두 본인만)
- 관리자는 Member 엔티티와 무관 — LDAP 헤더로만 식별
- 비회원도 상품/브랜드 정보를 볼 수 있어야 서비스 유입 가능

---

## 도메인별 기능 요구사항

### 1. 유저 (Member)

**사용자 관점:** 이커머스 서비스를 이용하기 위해 가입하고, 내 정보를 관리한다.

| ID | 기능 | 액터 | URI | 설명 | 구현 상태 |
|----|------|------|-----|------|-----------|
| U-1 | 회원가입 | Guest | `POST /api/v1/members` | loginId, password, name, birthDate, gender, email, phone | 기존 구현 (gender, phone 추가 필요) |
| U-2 | 내 정보 조회 | Member | `GET /api/v1/members/me` | 이름 마지막 글자 마스킹 | 기존 구현 |
| U-3 | 비밀번호 변경 | Member | `PATCH /api/v1/members/me/password` | 현재 비밀번호 검증 + 새 비밀번호 규칙 | 기존 구현 |
| U-4 | 내 정보 수정 | Member | `PATCH /api/v1/members/me` | 전화번호, 프로필 사진 수정 | 신규 |
| U-5 | 회원 탈퇴 | Member | `DELETE /api/v1/members/me` | 비밀번호 확인 후 soft delete | 신규 |

**검증 규칙:**
- loginId: 영문+숫자만, unique
- password: 8~16자, 영문대소문자/숫자/특수문자, 생년월일 미포함, bcrypt 암호화
- name: 한글만 또는 영문만, 공백 정규화
- gender: MALE / FEMALE
- email: 이메일 형식 검증
- phone: 전화번호 형식 검증
- profileImageUrl: URL 형식, nullable (파일 업로드 후 설정)
- 인증: `MemberAuthFilter`에서 헤더 추출 → `request.setAttribute("authenticatedMember", member)`
- 탈퇴: 비밀번호 확인 필수, soft delete 처리, 탈퇴 후 동일 loginId 재가입 불가

**경계 조건:**
- 삭제된(탈퇴한) 회원으로 로그인 시도 → UNAUTHORIZED (존재하지 않는 계정)
- 탈퇴 시 비밀번호 불일치 → BAD_REQUEST

---

### 2. 배송지 (Address)

**사용자 관점:** 자주 사용하는 배송지를 등록/관리하고, 주문 시 편리하게 선택한다.

| ID | 기능 | 액터 | URI | 설명 |
|----|------|------|-----|------|
| A-1 | 배송지 목록 조회 | Member | `GET /api/v1/members/me/addresses?page=&size=` | 내 배송지 전체 |
| A-2 | 배송지 등록 | Member | `POST /api/v1/members/me/addresses` | 첫 등록 시 자동 기본 배송지 |
| A-3 | 배송지 수정 | Member | `PUT /api/v1/members/me/addresses/{addressId}` | 수령인, 주소 등 |
| A-4 | 배송지 삭제 | Member | `DELETE /api/v1/members/me/addresses/{addressId}` | 삭제 |
| A-5 | 기본 배송지 설정 | Member | `PATCH /api/v1/members/me/addresses/{addressId}/default` | 기본 배송지 변경 |

**비즈니스 규칙:**
- 회원당 배송지 여러 개 등록 가능
- 하나의 배송지를 기본 배송지로 지정 (`isDefault`)
- 첫 번째 배송지 등록 시 자동으로 기본 배송지 설정
- **기본 배송지는 삭제 불가** — 다른 배송지를 기본으로 변경 후 삭제 가능
- 주문 시 선택한 배송지의 정보가 Order에 스냅샷으로 복사 (이후 Address 변경은 주문에 영향 없음)
- 배송지 필수 정보: 배송지명(label), 수령인 이름, 수령인 전화번호, 우편번호, 기본주소, 상세주소

**경계 조건:**
- 존재하지 않는 배송지 수정/삭제 → NOT_FOUND
- 타인의 배송지 접근 → NOT_FOUND
- 필수 필드 누락 → BAD_REQUEST
- 기본 배송지 삭제 시도 → BAD_REQUEST (다른 배송지를 기본으로 변경 후 삭제)

---

### 3. 브랜드 (Brand)

**사용자 관점:** 상품이 어떤 브랜드 소속인지 확인하고, 관심 브랜드를 좋아요 표시하고 싶다.

| ID | 기능 | 액터 | URI | 설명 |
|----|------|------|-----|------|
| B-1 | 브랜드 목록 조회 | Guest/Member | `GET /api/v1/brands?keyword=&page=&size=` | 키워드 검색 + 페이징 (keyword 없으면 전체) |
| B-2 | 브랜드 상세 조회 | Guest/Member | `GET /api/v1/brands/{brandId}` | 이름, 설명, 좋아요 수 |
| B-3 | 브랜드 등록 | Admin | `POST /api-admin/v1/brands` | 이름(필수), 설명 |
| B-4 | 브랜드 수정 | Admin | `PUT /api-admin/v1/brands/{brandId}` | 이름, 설명 변경 |
| B-5 | 브랜드 삭제 | Admin | `DELETE /api-admin/v1/brands/{brandId}` | soft delete + 해당 브랜드 상품도 함께 삭제 |

**비즈니스 규칙:**
- 브랜드 이름은 필수, 빈 문자열 불가, **동일 이름 불허 (unique)**
- 브랜드 삭제 시 해당 브랜드의 모든 상품도 soft delete (cascade)
- 브랜드 목록: `keyword` 파라미터로 브랜드명 검색 (LIKE), 미입력 시 전체 목록
- Admin은 조회 시 Public API 사용 (별도 Admin 조회 API 없음)

**경계 조건:**
- 브랜드명 null/blank → BAD_REQUEST
- 동일 브랜드명으로 등록 → BAD_REQUEST
- 존재하지 않는 브랜드 조회 → NOT_FOUND
- 존재하지 않는 브랜드 수정 → NOT_FOUND
- 이미 삭제된 브랜드 재삭제 → NOT_FOUND

---

### 4. 상품 (Product)

**사용자 관점:** 상품을 탐색하고, 정렬/필터로 원하는 상품을 찾고, 상세 정보를 확인한다.

| ID | 기능 | 액터 | URI | 설명 |
|----|------|------|-----|------|
| P-1 | 상품 목록 조회 | Guest/Member | `GET /api/v1/products?keyword=&brandId=&sort=latest&page=0&size=20` | 키워드 검색+페이징+정렬+브랜드필터 |
| P-2 | 상품 상세 조회 | Guest/Member | `GET /api/v1/products/{productId}` | 이름, 가격, 설명, 재고, 브랜드, 좋아요 수, 이미지 |
| P-3 | 상품 등록 | Admin | `POST /api-admin/v1/products` | 브랜드 존재 필수, 이미지 URL, 최대 주문 수량 |
| P-4 | 상품 수정 | Admin | `PUT /api-admin/v1/products/{productId}` | **브랜드 변경 불가**, 이미지/최대 주문 수량 변경 가능 |
| P-5 | 상품 삭제 | Admin | `DELETE /api-admin/v1/products/{productId}` | soft delete |

**비즈니스 규칙:**
- 상품은 반드시 하나의 브랜드에 속함 (등록 시 브랜드 존재 검증)
- 상품 수정 시 브랜드 변경 불가 (데이터 일관성)
- 가격은 양수, 재고는 0 이상, 최대 주문 수량은 양수
- 최대 주문 수량(`maxOrderQuantity`): 1회 주문 시 해당 상품을 주문할 수 있는 최대 수량 (Admin이 등록/수정 시 설정)
- 삭제된 상품은 조회 불가하지만 기존 주문의 스냅샷에는 영향 없음
- 정렬: `latest`(필수), `price_asc`, `likes_desc`(선택)
- imageUrl: nullable (이미지 없는 상품 허용)
- 상품 목록: `keyword` 파라미터로 상품명 또는 브랜드명 검색 (LIKE), 미입력 시 전체 목록
- Admin은 조회 시 Public API 사용 (별도 Admin 조회 API 없음)

**경계 조건:**
- 존재하지 않는 브랜드로 상품 등록 → BAD_REQUEST
- 삭제된 상품 조회 → NOT_FOUND
- 존재하지 않는 상품 수정/삭제 → NOT_FOUND
- 재고 0인 상품: 조회 가능, 주문 불가
- 가격 0원 상품: 허용하지 않음 (양수 필수)
- maxOrderQuantity <= 0 → BAD_REQUEST

---

### 5. 좋아요 (Like)

**사용자 관점:** 마음에 드는 상품이나 브랜드에 좋아요를 표시하고, 내가 좋아요한 목록을 관리한다.

#### 5-1. 상품 좋아요 (ProductLike)

| ID | 기능 | 액터 | URI | 설명 |
|----|------|------|-----|------|
| PL-1 | 상품 좋아요 토글 | Member | `POST /api/v1/products/{productId}/likes` | 좋아요 ↔ 취소 토글 |
| PL-2 | 내 상품 좋아요 목록 | Member | `GET /api/v1/members/me/likes/products?page=&size=` | 본인만 조회 가능 |

#### 5-2. 브랜드 좋아요 (BrandLike)

| ID | 기능 | 액터 | URI | 설명 |
|----|------|------|-----|------|
| BL-1 | 브랜드 좋아요 토글 | Member | `POST /api/v1/brands/{brandId}/likes` | 좋아요 ↔ 취소 토글 |
| BL-2 | 내 브랜드 좋아요 목록 | Member | `GET /api/v1/members/me/likes/brands?page=&size=` | 본인만 조회 가능 |

**비즈니스 규칙 (공통):**
- 토글 방식: 좋아요가 없으면 등록, 있으면 취소
- 토글/취소 시 대상의 like_count 동기화 (+1/-1)
- 토글 응답에 현재 상태 포함 (`liked: true/false`, `likeCount`)
- 좋아요 목록은 **본인만 조회 가능** (타인 접근 불가)
- 상품 좋아요 수는 상품 목록의 `likes_desc` 정렬에 활용

**경계 조건:**
- 존재하지 않는 대상에 좋아요 → NOT_FOUND
- 삭제된 상품/브랜드에 좋아요 → NOT_FOUND

---

### 6. 주문 (Order / OrderItem)

**사용자 관점:** 여러 상품을 한 번에 주문하고, 배송지를 지정하고, 내역을 확인하거나 취소한다.

| ID | 기능 | 액터 | URI | 설명 |
|----|------|------|-----|------|
| O-1 | 주문 생성 | Member | `POST /api/v1/orders` | 상품+수량, 배송지 |
| O-2 | 주문 목록 조회 | Member | `GET /api/v1/orders?startAt=&endAt=&page=&size=` | 날짜 범위 필터 + 페이징, 본인만 |
| O-3 | 주문 상세 조회 | Member | `GET /api/v1/orders/{orderId}` | 본인 주문만 (타인 → NOT_FOUND) |
| O-4 | 주문 취소 | Member | `POST /api/v1/orders/{orderId}/cancel` | 본인만. 재고 전체 복원 |
| O-5 | 주문 목록 (Admin) | Admin | `GET /api-admin/v1/orders?page=&size=` | 전체 주문 페이징 |
| O-6 | 주문 상세 (Admin) | Admin | `GET /api-admin/v1/orders/{orderId}` | 모든 주문 조회 가능 |
| O-7 | 주문 배송지 수정 | Member | `PUT /api/v1/orders/{orderId}/shipping-address` | 본인 주문, COMPLETED 상태만 |

**주문 요청 예시:**
```json
{
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 3, "quantity": 1 }
  ],
  "addressId": 1
}
```

**비즈니스 규칙:**
- 주문 시 배송지 필수 (`addressId`) — 선택한 배송지 정보를 Order에 스냅샷 저장
- 주문 배송지 수정: Order의 스냅샷 필드를 직접 수정 (원본 Address에 영향 없음)
- 주문 시 재고 선차감 (Pre-deduction)
- **일부 상품만 재고 부족해도 전체 주문 실패** (부분 성공 없음)
- 동일 상품 중복 시 수량 합산 (하나의 OrderItem으로 병합)
- OrderItem에 주문 시점 스냅샷 저장 (productName, productPrice)
- 금액: totalAmount = 각 OrderItem의 (productPrice × quantity) 합산
- 주문 상태: COMPLETED → CANCELLED (역방향 없음)
- 취소 시: 재고 전체 복원
- 결제는 현재 제외 — 주문 생성 즉시 COMPLETED
- Order 배송지 스냅샷: recipientName, recipientPhone, zipCode, address1, address2

**경계 조건:**
- 빈 items 배열 → BAD_REQUEST
- quantity <= 0 → BAD_REQUEST
- addressId 누락 또는 존재하지 않는 배송지 → BAD_REQUEST
- 타인의 배송지로 주문 시도 → BAD_REQUEST
- 삭제된 상품으로 주문 → NOT_FOUND
- 재고 부족 → BAD_REQUEST (어떤 상품이 부족한지 메시지 포함)
- 주문 수량이 상품의 maxOrderQuantity 초과 → BAD_REQUEST
- 이미 취소된 주문 재취소 → NOT_FOUND
- 타인의 주문 취소 시도 → NOT_FOUND (존재를 노출하지 않음)
- 타인의 주문 상세 조회 → NOT_FOUND (존재를 노출하지 않음)
- 타인의 주문 배송지 수정 시도 → NOT_FOUND (존재를 노출하지 않음)
- CANCELLED 상태의 주문 배송지 수정 → BAD_REQUEST

---

### 7. 관리자 (Admin)

**사용자(관리자) 관점:** 브랜드와 상품을 등록/수정/삭제하고, 회원 정보를 조회하고, 전체 주문 내역을 관리한다.

| ID | 기능 | URI | 설명 |
|----|------|-----|------|
| AD-1 | 브랜드 등록 | `POST /api-admin/v1/brands` | B-3 |
| AD-2 | 브랜드 수정 | `PUT /api-admin/v1/brands/{brandId}` | B-4 |
| AD-3 | 브랜드 삭제 | `DELETE /api-admin/v1/brands/{brandId}` | B-5, 상품 cascade |
| AD-4 | 상품 등록 | `POST /api-admin/v1/products` | P-3 |
| AD-5 | 상품 수정 | `PUT /api-admin/v1/products/{productId}` | P-4, 브랜드 변경 불가 |
| AD-6 | 상품 삭제 | `DELETE /api-admin/v1/products/{productId}` | P-5 |
| AD-7 | 상품 재고 수정 | `PATCH /api-admin/v1/products/{productId}/stock` | 입고/재고 조정 |
| AD-8 | 회원 목록 조회 | `GET /api-admin/v1/members?keyword=&page=&size=` | 키워드로 회원 검색 (loginId, name) |
| AD-9 | 회원 상세 조회 | `GET /api-admin/v1/members/{memberId}` | 특정 회원 정보 확인 |
| AD-10 | 주문 목록 조회 | `GET /api-admin/v1/orders?memberId=&page=&size=` | O-5, 전체 주문 + 회원별 필터 |
| AD-11 | 주문 상세 조회 | `GET /api-admin/v1/orders/{orderId}` | O-6, 소유권 무관 |

**인증 방식:**
- 헤더: `X-Loopers-Ldap: loopers.admin`
- Member 엔티티와 무관 — LDAP 헤더 값으로만 식별
- `AdminAuthFilter`에서 `/api-admin/v1/**` 경로에 대해 헤더 검증

**비즈니스 규칙:**
- 브랜드/상품 **조회는 Public API 공유** (별도 Admin 조회 API 없음)
- 회원 목록: `keyword`로 loginId 또는 name 검색 (LIKE), 미입력 시 전체 목록
- 회원 상세: 마스킹 없이 원본 정보 반환 (CS 대응용, password 제외)
- 주문 목록: `memberId` 파라미터로 특정 회원의 주문만 필터 가능, 미입력 시 전체
- 주문 수정/취소 권한 없음 (조회 전용)
- 상품 재고 수정: 재고 수량을 직접 설정 (입고/조정 용도)
- 브랜드 삭제 시 해당 브랜드의 상품도 함께 soft delete (cascade)

**경계 조건:**
- LDAP 헤더 없이 Admin API 접근 → UNAUTHORIZED
- 잘못된 LDAP 값으로 접근 → UNAUTHORIZED
- 존재하지 않는 회원 조회 → NOT_FOUND
- 존재하지 않는 상품 재고 수정 → NOT_FOUND
- 존재하지 않는 주문 조회 → NOT_FOUND
- 재고 수량 음수 설정 → BAD_REQUEST

---

### 8. 파일 업로드 (File)

**사용자 관점:** 프로필 사진이나 상품 이미지를 업로드한다.

| ID | 기능 | 액터 | URI | 설명 |
|----|------|------|-----|------|
| F-1 | 파일 업로드 | Member/Admin | `POST /api/v1/files` | 이미지 업로드 → URL 반환 |

**비즈니스 규칙:**
- 업로드된 파일은 스토리지에 저장 후 접근 가능한 URL을 반환
- 반환된 URL을 Member.profileImageUrl 또는 Product.imageUrl에 설정
- 허용 파일 형식: 이미지 (JPEG, PNG, WEBP)
- 파일 크기 상한 설정 필요

**경계 조건:**
- 허용되지 않은 파일 형식 → BAD_REQUEST
- 파일 크기 초과 → BAD_REQUEST

---

## 합의된 정책

| 항목 | 결정 |
|------|------|
| 결제 | 제외 — 주문 즉시 COMPLETED |
| 재고 차감 | 선차감 (Pre-deduction) |
| 주문 실패 | 트랜잭션 롤백 |
| 주문 취소 | 재고 전체 복원 |
| 동시성 제어 | 기본 기능 구현 후 별도 단계 |
| 좋아요 방식 | 토글 (POST 1개 엔드포인트, 있으면 취소 / 없으면 등록) |
| 좋아요 수 전략 | Product/Brand에 like_count 비정규화 컬럼 |
| 주문 동일상품 중복 | 수량 합산 처리 |
| 키워드 검색 | 상품 목록은 상품명+브랜드명 LIKE 검색, 브랜드 목록은 브랜드명 LIKE 검색 |
| Admin 조회 | Brand/Product는 Public API 공유, Order만 Admin 전용 조회 |
| 좋아요 목록 | 본인만 조회 가능 |
| 배송지 관리 | Address 별도 엔티티 (Member:Address = 1:N), 주문에 스냅샷 |
| 주문 배송지 | 원본 Address와 독립적으로 수정 가능 |
| 파일 업로드 | URL 반환 방식, 프로필 사진/상품 이미지에 사용 |

---

## 구현 범위

### In Scope
- Member (기존 유지 + gender, phone, profileImageUrl 추가)
- Address (배송지 관리)
- Brand, Product (imageUrl 포함)
- ProductLike, BrandLike
- Order, OrderItem (배송지 스냅샷 포함)
- Admin (LDAP 인증, 브랜드/상품 CUD, 재고 수정, 회원 조회, 주문 조회)
- 주문 취소, 주문 배송지 수정
- 파일 업로드 (이미지)

### Out Scope
- 결제 (PG 연동)
- 배송 추적 / 배송 상태 관리
- 환불
- 알림
- 랭킹 / 추천
- 동시성 제어 (후속 단계)
- 쿠폰 (Coupon, MemberCoupon — 할인 체계 전체)
- 회원 등급 (별도 도메인, 현재 사용처 없음)
- 적립금 (별도 도메인, 주문 연동 필요)

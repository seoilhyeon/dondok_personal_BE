# 포인트

## `POST /api/points/charges`

> TossPayments 결제를 서버에서 승인 확인한 뒤 도딘(포인트)을 충전한다.

**Request**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `payment_id` | `string` | Y | TossPayments `paymentKey`. 서버 로컬 멱등성의 기준 키다. |
| `order_id` | `string` | Y | FE가 Toss redirect 전에 생성한 TossPayments `orderId`. 완료된 결제 재시도 시 같은 값이어야 한다. |
| `amount` | `integer` | Y | 원화 충전 금액. 1,000원 이상 1,000,000원 이하, 1,000원 단위. |

**Response** `201 Created`

```json
{
  "point_history_id": 3001,
  "member_uuid": "018f4fd2-6d7a-7a41-9f58-6d07f5c3c901",
  "amount": 50000,
  "balance_after": 350000,
  "transaction_type": "POINT_CHARGE",
  "created_at": "2026-05-07T09:30:00+09:00"
}
```

동일한 `payment_id`, `order_id`, `amount`로 재시도하면 기존 `point_history` 결과를 `200 OK`로 반환한다.

**Error**

- `INVALID_INPUT` - API 요청 body 검증 실패. 예: `payment_id` 누락/길이 초과, `order_id` 형식 오류, `amount` 범위 또는 1,000원 단위 위반.
- `INVALID_AMOUNT`
- `IDEMPOTENCY_CONFLICT`
- `PAYMENT_CONFIRM_FAILED`
- `PAYMENT_CONFIRM_MISMATCH`
- `PAYMENT_CONFIRM_STALE`

**정책**

- 이 endpoint는 Toss redirect success만 신뢰하지 않는다. 서버가 Toss `POST /v1/payments/confirm`을 호출해 승인 확인한 뒤에만 포인트를 적립한다.
- 서버는 `payment_id` 기준으로 `point_charge` row를 만들고, 원장 생성 성공 후 `point_charge.point_history_id`를 연결한다.
- 충전 원장의 멱등성 키는 `point_history.idempotency_key = charge:{payment_id}`다.
- `point_history` 생성과 `point_account.available_balance` 증가는 같은 트랜잭션에서 처리한다.
- 충전 완료 판정은 `point_history_id != null` 기준이다. `status` 문자열만으로 성공 여부를 판단하지 않는다.
- 이미 원장에 연결된 `payment_id`가 다른 member/order/amount로 재시도되면 `IDEMPOTENCY_CONFLICT`를 반환한다.
- 아직 원장에 연결되지 않은 같은 사용자 `payment_id`의 실패/대기 row는 동일한 `order_id`와 `amount`일 때만 confirm을 재시도한다. `payment_id`는 Toss `paymentKey`이며 `order_id`와 1:1로 취급하므로, 다른 `order_id` 또는 `amount`로 재시도하면 `IDEMPOTENCY_CONFLICT`를 반환한다.
- Toss confirm 결과의 `paymentKey`, `orderId`, `totalAmount`, `currency=KRW`, `status=DONE`이 요청과 일치할 때만 원장을 생성한다.
- confirm 진행 중 row의 canonical 입력이 바뀌면 원장을 생성하지 않고 `PAYMENT_CONFIRM_STALE`을 반환하며, 이미 승인된 결제에 대해서는 보상 cancel을 시도한다.
- Toss confirm이 `DONE`으로 끝난 뒤 원장 생성이 영구 도메인 오류(`POINT_ACCOUNT_NOT_FOUND`, `IDEMPOTENCY_CONFLICT`, `PAYMENT_CONFIRM_STALE` 등)로 실패하면 서버는 Toss cancel을 보상 호출하고 `point_charge`를 실패 상태로 기록한다. DB 연결 장애 같은 일시적 런타임 실패는 row를 `PENDING_CONFIRM`으로 남겨 같은 요청 재시도로 원장 연결을 복구할 수 있게 한다.
- Toss secret key는 서버 설정으로만 사용하며 FE/API 응답에 노출하지 않는다.
- `balance_after`는 `point_history.available_after`의 read-only alias다. 별도 persisted column이 아니다.
- 컨트롤러 Bean Validation 단계에서 거절되는 요청은 `INVALID_INPUT`을 반환한다. 서비스 방어 검증에서만 감지된 금액 오류는 `INVALID_AMOUNT`를 반환한다.

---

## `GET /api/points`

> 현재 포인트 잔액 상세(가용·예약·잠금)를 조회한다.

**Response** `200 OK`

```json
{
  "available_balance": 350000,
  "reserved_balance": 100000,
  "active_locked_amount": 60000,
  "settlement_pending_amount": 40000,
  "settlement_failed_amount": 0,
  "locked_balance": 100000,
  "total_balance": 550000,
  "updated_at": "2026-05-07T09:30:00+09:00"
}
```

**정책**

| 필드 | 설명 |
|------|------|
| `available_balance` | 현재 사용 가능한 잔액 |
| `reserved_balance` | `PENDING` 상태 참여의 보증금 |
| `active_locked_amount` | `RECRUITING`/`ACTIVE` 크루의 `LOCKED` 보증금 |
| `settlement_pending_amount` | `settlement_item.refund_amount` 기준 미지급 정산 환급 예정액. `point_history_id IS NULL`이고 settlement 상태가 `PENDING`/`RUNNING`/`RETRY_WAIT`인 항목 합계 |
| `settlement_failed_amount` | `settlement_item.refund_amount` 기준 지급 실패 후 복구가 필요한 미지급 정산 환급액. `point_history_id IS NULL`이고 settlement 상태가 `FAILED`인 항목 합계 |
| `locked_balance` | `point_account.locked_balance`. `LOCKED` 크루 보증금 총액 persisted bucket |
| `total_balance` | `available_balance + reserved_balance + locked_balance` |

- `available_balance`, `reserved_balance`, `locked_balance`는 `point_account`의 persisted balance bucket이다. 포인트 변경 커맨드는 `point_account` bucket 변경과 `point_history` append/reuse를 같은 트랜잭션 안에서 처리한다.
- `active_locked_amount`는 `locked_balance`를 현재 크루 상태로 설명하는 read-time projection이며 DB/account 컬럼으로 저장하지 않는다.
- `settlement_pending_amount`는 locked principal split이 아니라 settlement-result projection이다. `active_locked_amount + settlement_pending_amount = locked_balance` 불변식을 두지 않는다. 불일치/이상값은 `settlement_item.refund_amount`, `settlement_item.point_history_id`, `settlement.status`, `point_history`, `crew_participant`, `point_account`를 함께 대조한다.
- 이 필드들은 출금 가능 여부, 정산 결과 판단에 사용하지 않는다.
- `CANCELLED` 상태의 reserve는 반환 완료 상태이므로 `reserved_balance` 합산 대상이 아니다. 동일 row가 이후 reopen되어 `PENDING`으로 복귀하면 새 사이클의 reserve가 `reserved_balance` projection에 다시 합산된다.

---

## `GET /api/points/history`

> 포인트 입출금 거래 이력을 최신순으로 페이지네이션 조회한다.

**Query**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `limit` | `integer` | N | 기본 20, 최대 100 |
| `cursor` | `string` | N | 페이지네이션 커서 |
| `type` | `string` | N | 유형 필터. `charge`, `refund`, `deposit`, `withdrawal`, `settlement` 중 하나 |
| `month` | `string` | N | 월별 필터. `YYYY-MM` 형식. 예: `2026-06` |

**Response** `200 OK`

```json
{
  "items": [
    {
      "point_history_id": 3001,
      "amount": 50000,
      "balance_after": 350000,
      "transaction_type": "POINT_CHARGE",
      "reference_type": "POINT_CHARGE",
      "reference_id": 0,
      "reference_meta": null,
      "created_at": "2026-05-07T09:30:00+09:00"
    },
    {
      "point_history_id": 3000,
      "amount": -30000,
      "balance_after": 300000,
      "transaction_type": "CREW_DEPOSIT_RESERVE",
      "reference_type": "CREW_PARTICIPANT",
      "reference_id": 9001,
      "reference_meta": {
        "crew_id": 42,
        "crew_title": "새벽 기상 챌린지"
      },
      "created_at": "2026-05-06T21:00:00+09:00"
    }
  ],
  "next_cursor": "djF8MjAyNi0wNS0wN1QwOTozMDowMCswOTowMHwzMDAx"
}
```

**Error**

- `INVALID_LIMIT`
- `INVALID_CURSOR`
- `INVALID_HISTORY_TYPE`
- `INVALID_HISTORY_MONTH`

예시 요청:

```http
GET /api/points/history?limit=20
GET /api/points/history?limit=20&cursor=djF8MjAyNi0wNS0wN1QwOTozMDowMCswOTowMHwzMDAx
GET /api/points/history?type=deposit&month=2026-06&limit=20
```

**정책**

- 최신순(`created_at DESC, point_history_id DESC`) 정렬
- `type`은 표시용 유형 필터이며 내부 `transaction_type`과 다음처럼 매핑한다.

| `type` | 화면 라벨 | 포함 `transaction_type` | 설명 |
|---|---|---|---|
| `charge` | 도딘충전 | `POINT_CHARGE` | TossPayments 결제 확인 후 충전된 도딘 |
| `refund` | 환급 | `CREW_RESERVE_RELEASE`, `CREW_CANCEL_REFUND` | 신청 취소/거절/만료 또는 크루 해체로 반환된 예치 도딘 |
| `deposit` | 도딘예치 | `CREW_DEPOSIT_RESERVE`, `CREW_DEPOSIT_LOCK` | 크루 신청 reserve와 승인 lock 확정 이력 |
| `withdrawal` | 도딘출금 | 현재 없음 | MVP 현재 출금 원장 타입 미지원. 요청은 허용하지만 빈 목록을 반환한다 |
| `settlement` | 정산 | `CREW_SETTLEMENT_REFUND` | 정산 완료 후 지급된 환급 도딘 |

- `type`이 비어 있으면 전체 유형을 조회한다. 정의되지 않은 값이면 `INVALID_HISTORY_TYPE`를 반환한다.
- `month`가 비어 있으면 전체 기간을 조회한다. 지정 시 해당 월의 `[YYYY-MM-01 00:00, 다음 달 1일 00:00)` 범위로 `created_at`을 필터한다.
- `month` 형식이 `YYYY-MM`이 아니거나 해석할 수 없으면 `INVALID_HISTORY_MONTH`를 반환한다.
- `cursor`는 URL-safe Base64 opaque cursor다. 클라이언트는 해석하거나 직접 생성하지 않고 이전 응답의 `next_cursor`를 다음 요청에 그대로 전달한다.
- 서버는 padding이 생략된 cursor도 복원해 해석한다. cursor 내부 버전/구조는 서버 구현 세부사항이며 변경될 수 있다.
- `next_cursor`는 다음 slice가 존재할 때만 응답에 포함하며, 없거나 `null`이면 더 조회할 slice가 없다.
- `has_next`, `total_count` 같은 page total 필드는 MVP 필수 contract가 아니다.
- `limit`이 `1` 미만이거나 `100`을 초과하면 `INVALID_LIMIT`를 반환한다.
- `cursor` 형식이 잘못되었거나 해석할 수 없으면 `INVALID_CURSOR`를 반환한다.
- `balance_after`는 `point_history.available_after`의 read-only alias다. 별도 persisted column이 아니며, `point_history.reserved_after`·`locked_after`는 reconciliation/debug 전용으로 API에 노출하지 않는다.
- `reference_meta`는 내역 표시용 read-model이며 persisted `point_history` column이 아니다. 크루와 연결된 이력(`CREW_PARTICIPANT`, `SETTLEMENT_ITEM`)에만 `{ "crew_id", "crew_title" }`를 내려주고, 충전/출금처럼 크루 맥락이 없는 이력은 `null`이다.
- `reference_meta`는 사용자에게 보여줄 최소 표시 정보만 담는다. 내부 `crew_participant.id`, `settlement_item.id`, member 내부 ID, 정산 계산 상세는 포함하지 않는다.

---

## `GET /api/points/wallet-history`

> 지갑 화면에 표시할 사용자 관점의 포인트 이벤트 내역을 최신순으로 페이지네이션 조회한다. 기존 `GET /api/points/history`는 append-only raw ledger 조회 API로 유지하며, 이 API는 표시 이벤트 단위로 `CREW_DEPOSIT_RESERVE` / `CREW_DEPOSIT_LOCK`을 정규화한다.

**Query**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `limit` | integer | N | 기본 20, 최대 100 |
| `cursor` | string | N | 이전 응답의 `next_cursor` |
| `type` | string | N | `charge`, `deposit`, `refund`, `withdrawal`, `settlement` |
| `from` | string | N | `YYYY-MM-DD`. Seoul local date range start inclusive |
| `to` | string | N | `YYYY-MM-DD`. Seoul local date range end exclusive |
| `month` | string | N | `YYYY-MM`. Deprecated compatibility shim. `point_history.created_at`의 Seoul local month 기준 |

**Response** `200 OK`

```json
{
  "items": [
    {
      "wallet_event_id": "crew-deposit:9001",
      "amount": -30000,
      "balance_after": 300000,
      "display_type": "DODIN_DEPOSIT",
      "status": "CONFIRMED",
      "reference_type": "CREW_PARTICIPANT",
      "reference_id": 9001,
      "reference_meta": {
        "crew_id": 42,
        "crew_title": "새벽 기상 챌린지"
      },
      "created_at": "2026-05-06T21:00:00+09:00"
    }
  ],
  "next_cursor": "djF8MjAyNi0wNS0wNlQyMTowMDowMCswOTowMHxjcmV3LWRlcG9zaXQ6OTAwMQ"
}
```

**표시 이벤트 매핑**

| Raw transaction state | `display_type` | `status` | 설명 |
|---|---|---|---|
| `POINT_CHARGE` | `DODIN_CHARGE` | `COMPLETED` | 충전 완료 |
| `CREW_DEPOSIT_RESERVE` only | `DODIN_DEPOSIT` | `PENDING` | 예치 신청 |
| `CREW_DEPOSIT_RESERVE` + matching `CREW_DEPOSIT_LOCK` | `DODIN_DEPOSIT` | `CONFIRMED` | 예치 신청과 확정을 단일 표시 이벤트로 노출 |
| `CREW_DEPOSIT_LOCK` only | `DODIN_DEPOSIT` | `CONFIRMED` | 호스트/즉시 LOCK 등 reserve row가 없는 확정 예치도 숨기지 않음 |
| `CREW_RESERVE_RELEASE` | `DODIN_DEPOSIT_REFUND` | `RELEASED` | PENDING 예치 반환 |
| `CREW_CANCEL_REFUND` | `DODIN_DEPOSIT_REFUND` | `RELEASED` | LOCKED 예치 반환 |
| `CREW_SETTLEMENT_REFUND` | `SETTLEMENT_REFUND` | `COMPLETED` | 정산 환급 완료 |

**정책**

- 표시 이벤트 최신순(`created_at DESC, wallet_event_id DESC`) 정렬이다.
- `limit`과 `next_cursor`는 raw `point_history` row가 아니라 표시 이벤트 기준으로 동작한다.
- `wallet_event_id`는 표시 이벤트 식별자이며 내부 DB member id를 포함하지 않는다. 클라이언트는 cursor와 마찬가지로 구조를 해석하지 않는다.
- `CREW_DEPOSIT_RESERVE`와 같은 `CREW_PARTICIPANT` reference의 matching `CREW_DEPOSIT_LOCK`은 하나의 `DODIN_DEPOSIT / CONFIRMED` 이벤트로 접는다.
- `CREW_DEPOSIT_LOCK`만 존재하는 이벤트는 `DODIN_DEPOSIT / CONFIRMED`로 노출한다.
- grouped deposit의 `amount`, `balance_after`, `created_at`은 사용 가능 잔액이 줄어드는 사용자 체감 시점인 reserve row를 기준으로 한다. lock-only는 lock row 기준이다.
- `cursor`는 URL-safe Base64 opaque cursor다. 클라이언트는 이전 응답의 `next_cursor`를 그대로 전달한다.
- `from` / `to` are date-only range params interpreted as Seoul local calendar dates. The range is half-open: `[from, to)`.
- `from` and `to` must be supplied together. `from >= to` is invalid.
- `month` is kept for compatibility and maps to `[first day of month, first day of next month)`. New callers should prefer `from` / `to`.
- `month` cannot be combined with either `from` or `to`.
- Date params may be omitted for backward-compatible low-volume recent-summary calls.
- Error는 `/api/points/history`와 동일하게 `INVALID_LIMIT`, `INVALID_CURSOR`, `INVALID_HISTORY_TYPE`, `INVALID_HISTORY_MONTH`, `INVALID_HISTORY_RANGE`를 사용한다.

**`reference_type` / `reference_id` / `idempotency_key` 매핑**

| 도메인 동작 | `transaction_type` | `reference_type` | `reference_id` | `idempotency_key` |
|---|---|---|---|---|
| 포인트 충전 | `POINT_CHARGE` | `POINT_CHARGE` | `0` | `charge:{payment_id}` |
| 크루 참여 보증금 reserve | `CREW_DEPOSIT_RESERVE` | `CREW_PARTICIPANT` | `crew_participant.id` | `crew:{crewId}:participant:{participantId}:reserve:{cycle}` |
| 크루 참여 승인 확정(PENDING→LOCKED) | `CREW_DEPOSIT_LOCK` | `CREW_PARTICIPANT` | `crew_participant.id` | `crew:{crewId}:participant:{participantId}:reserve-lock:{cycle}` |
| PENDING reserve 반환 | `CREW_RESERVE_RELEASE` | `CREW_PARTICIPANT` | `crew_participant.id` | `crew:{crewId}:participant:{participantId}:reserve-release:{cycle}` |
| LOCKED 예치 반환 | `CREW_CANCEL_REFUND` | `CREW_PARTICIPANT` | `crew_participant.id` | `crew:{crewId}:participant:{participantId}:crew-cancel-refund` |
| 일반 정산 환급 | `CREW_SETTLEMENT_REFUND` | `SETTLEMENT_ITEM` | `settlement_item.id` | `crew:{crewId}:participant:{participantId}:settlement-refund:final` |

- `POINT_CHARGE.reference_id = 0`은 충전 원장이 별도 내부 aggregate를 참조하지 않는 sentinel이다. 요청 필드 `payment_id`는 `idempotency_key = charge:{payment_id}`로만 저장하며, 응답의 `point_history_id`가 생성된 원장 row를 식별한다. `point_history.id`를 자기 자신의 `reference_id`로 사후 업데이트하지 않는다.
- `CREW_DEPOSIT_RESERVE`는 보증금 reserve 이벤트다. 일반 `PENDING` 신청은 `available_balance -= deposit_amount` / `reserved_balance += deposit_amount`, host auto-created `LOCKED`는 `available_balance -= deposit_amount` / `locked_balance += deposit_amount`로 반영한다.
- `CREW_DEPOSIT_LOCK`는 `PENDING` 승인 시점(예약 자산이 잠금 자산으로 확정)에서 `reserved_balance -= deposit_amount` / `locked_balance += deposit_amount`로 반영하는 승인 이벤트다.
- 승인 시점 `PENDING → LOCKED` 전이는 `reserved_balance → locked_balance` bucket transition 수행하며, `CREW_DEPOSIT_LOCK`로 `point_history`를 append/reuse한다.
- `{cycle}`은 같은 `crew_participant.id`가 `CANCELLED → PENDING`으로 reopen될 때 이전 reserve/release와 새 reserve/release를 구분하는 deterministic suffix다. 최초 사이클은 `1`이다. 새 reserve cycle은 해당 participant의 누적 `CREW_RESERVE_RELEASE` 원장 수 + 1로 계산한다. reserve 원장 수를 세어 cycle을 증가시키면 duplicate reserve retry가 cycle을 밀 수 있으므로 사용하지 않는다.
- release는 `crew_participant.released_point_history_id`가 이미 있으면 기존 release 원장을 재사용하고, 없을 때만 현재 cycle의 `CREW_RESERVE_RELEASE`를 append한 뒤 같은 트랜잭션에서 `released_point_history_id`를 연결한다. `CANCELLED → PENDING` reopen 시 이 FK를 `null`로 reset해 다음 cycle release를 허용한다.
- 정산 환급 idempotency key는 runtime-generated `settlement.id`에 의존하지 않는다. crew/participant 자연키와 최종 정산 1회를 뜻하는 `final` suffix를 사용해 같은 participant의 최종 환급 중복 지급을 차단한다.
- 정산 환급의 `settlement_item.id`는 지급 근거 스냅샷 추적용 linkage이며 `reference_id`와 `settlement_item.point_history_id`에 남긴다.
- 동일 `settlement-refund:final` key 재시도는 기존 원장을 재사용/연결한다. 단, 같은 key인데 `settlement_item.id`, 환급 금액, 정산 algorithm version, 인정 성공 수 등 canonical payout input이 다르면 idempotency conflict로 실패해야 한다.
- 추후 재정산/보정 지급이 필요하면 `final`을 재사용하지 않고 별도 transaction type/key(예: `settlement-adjustment:{adjustmentId}`)로 분리한다.

## 결제 승인 후 포인트 미적립 복구 스위퍼

- `point_charge.status = PENDING_CONFIRM`이고 `point_history_id IS NULL`인 충전 건이 일정 시간 이상 남아 있으면 복구 스위퍼 대상이 된다.
- 스위퍼는 Toss 결제 조회 API(`GET /v1/payments/{paymentKey}`)로 실제 결제 상태를 확인한다.
- Toss 조회 결과가 `status = DONE`, `currency = KRW`이고 `paymentKey`, `orderId`, `totalAmount`가 로컬 `point_charge`의 `payment_id`, `order_id`, `amount`와 모두 일치할 때만 포인트 원장을 생성/재사용한다.
- 포인트 원장은 기존 충전 경로와 동일하게 `point_history.idempotency_key = charge:{payment_id}`를 사용한다. 따라서 API 재시도와 스위퍼가 같은 결제를 처리해도 동일 원장을 재사용해야 한다.
- Toss 조회 실패 또는 아직 `DONE`이 아닌 결제는 실패 확정하지 않고 다음 배치에서 다시 확인한다.
- Toss 조회 결과가 로컬 충전 요청과 일치하지 않으면 포인트를 적립하지 않고 `CONFIRM_FAILED`로 기록한다.
- Toss 네트워크 조회는 DB row lock 밖에서 수행하고, 최종 원장 연결만 짧은 트랜잭션 안에서 `point_charge`를 재조회/lock 후 처리한다.
- 반복 실패 또는 미완료 결제가 같은 배치마다 무한 반복되지 않도록 `recovery_attempt_count`, `next_recovery_at` 기준으로 재시도 간격과 최대 재시도 횟수를 제한한다.

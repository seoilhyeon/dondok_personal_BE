# 포인트

## `POST /api/points/charges`

> TossPayments 결제를 확인하여 포인트를 충전한다.

**Request**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `payment_id` | `string` | Y | TossPayments `paymentKey`. 하나의 충전 이벤트만 의미해야 한다 |
| `order_id` | `string` | Y | TossPayments `orderId`. confirm 검증과 로그 상관관계 추적용이며 idempotency key로 사용하지 않는다 |
| `amount` | `integer` | Y | 충전 금액 |

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

동일 `payment_id` 재시도 시 `200 OK`로 기존 결과를 반환한다.

**Error**

- `INVALID_AMOUNT`
- `INVALID_PAYMENT_ID`
- `PAYMENT_ID_REUSED_WITH_DIFFERENT_AMOUNT`

**정책**

- Toss confirm 성공 확인 후에만 포인트 원장을 생성한다.
- idempotency key: `charge:{payment_id}`
- 동일 `payment_id` + 다른 금액은 conflict로 실패한다.
- `balance_after`는 `point_history.available_after`의 read-only alias다. 별도 persisted column이 아니다.

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
| `settlement_pending_amount` | `CLOSED` 크루의 정산 전 `LOCKED` 보증금 |
| `locked_balance` | `point_account.locked_balance`. `LOCKED` 크루 보증금 총액 persisted bucket |
| `total_balance` | `available_balance + reserved_balance + locked_balance` |

- `available_balance`, `reserved_balance`, `locked_balance`는 `point_account`의 persisted balance bucket이다. 포인트 변경 커맨드는 `point_account` bucket 변경과 `point_history` append/reuse를 같은 트랜잭션 안에서 처리한다.
- `active_locked_amount`, `settlement_pending_amount`는 `locked_balance`를 현재 크루/정산 상태로 나누어 설명하는 read-time projection split이며 DB/account 컬럼으로 저장하지 않는다.
- 조회 시 `active_locked_amount + settlement_pending_amount = locked_balance`가 되도록 집계한다. 불일치가 발견되면 `point_history`, `crew_participant`, `settlement_item` linkage와 `point_account`를 함께 대조한다.
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
  "next_cursor": "2026-05-07T09:30:00+09:00_3001"
}
```

**Error**

- `INVALID_LIMIT`
- `INVALID_CURSOR`

예시 요청:

```http
GET /api/points/history?limit=20
GET /api/points/history?limit=20&cursor=2026-05-07T09:30:00+09:00_3001
```

**정책**

- 최신순(`created_at DESC, point_history_id DESC`) 정렬
- `cursor`는 클라이언트가 해석하지 않고 다음 요청에 그대로 전달한다.
- `next_cursor`는 다음 slice가 존재할 때만 응답에 포함하며, 없거나 `null`이면 더 조회할 slice가 없다.
- `has_next`, `total_count` 같은 page total 필드는 MVP 필수 contract가 아니다.
- `limit`이 `1` 미만이거나 `100`을 초과하면 `INVALID_LIMIT`를 반환한다.
- `cursor` 형식이 잘못되었거나 해석할 수 없으면 `INVALID_CURSOR`를 반환한다.
- `balance_after`는 `point_history.available_after`의 read-only alias다. 별도 persisted column이 아니며, `point_history.reserved_after`·`locked_after`는 reconciliation/debug 전용으로 API에 노출하지 않는다.
- `reference_meta`는 내역 표시용 read-model이며 persisted `point_history` column이 아니다. 크루와 연결된 이력(`CREW_PARTICIPANT`, `SETTLEMENT_ITEM`)에만 `{ "crew_id", "crew_title" }`를 내려주고, 충전/출금처럼 크루 맥락이 없는 이력은 `null`이다.
- `reference_meta`는 사용자에게 보여줄 최소 표시 정보만 담는다. 내부 `crew_participant.id`, `settlement_item.id`, member 내부 ID, 정산 계산 상세는 포함하지 않는다.

**`reference_type` / `reference_id` / `idempotency_key` 매핑**

| 도메인 동작 | `transaction_type` | `reference_type` | `reference_id` | `idempotency_key` |
|---|---|---|---|---|
| 포인트 충전 | `POINT_CHARGE` | `POINT_CHARGE` | `0` | `charge:{payment_id}` |
| 크루 참여 보증금 reserve/lock event | `CREW_DEPOSIT_RESERVE` | `CREW_PARTICIPANT` | `crew_participant.id` | `crew:{crewId}:participant:{participantId}:reserve:{cycle}` |
| PENDING reserve 반환 | `CREW_RESERVE_RELEASE` | `CREW_PARTICIPANT` | `crew_participant.id` | `crew:{crewId}:participant:{participantId}:reserve-release:{cycle}` |
| 일반 정산 환급 | `CREW_SETTLEMENT_REFUND` | `SETTLEMENT_ITEM` | `settlement_item.id` | `crew:{crewId}:participant:{participantId}:settlement-refund:final` |

- `POINT_CHARGE.reference_id = 0`은 충전 원장이 별도 내부 aggregate를 참조하지 않는 sentinel이다. 요청 필드 `payment_id`는 `idempotency_key = charge:{payment_id}`로만 저장하며, 응답의 `point_history_id`가 생성된 원장 row를 식별한다. `point_history.id`를 자기 자신의 `reference_id`로 사후 업데이트하지 않는다.
- `CREW_DEPOSIT_RESERVE`는 보증금 reserve/lock 이벤트다. 일반 참여자의 `PENDING` 신청은 `available_balance -= deposit_amount` / `reserved_balance += deposit_amount`이고, host auto-created `LOCKED` 참여는 `available_balance -= deposit_amount` / `locked_balance += deposit_amount`다. 두 경우 모두 같은 `transaction_type`을 사용하며 별도 host 전용 transaction type을 만들지 않는다.
- 승인 시점 `PENDING → LOCKED` 전이는 `reserved_balance → locked_balance` bucket transition만 수행하며 새 `point_history` row를 만들지 않는다.
- `{cycle}`은 같은 `crew_participant.id`가 `CANCELLED → PENDING`으로 reopen될 때 이전 reserve/release와 새 reserve/release를 구분하는 deterministic suffix다. 최초 사이클은 `1`이다. 새 reserve cycle은 해당 participant의 누적 `CREW_RESERVE_RELEASE` 원장 수 + 1로 계산한다. reserve 원장 수를 세어 cycle을 증가시키면 duplicate reserve retry가 cycle을 밀 수 있으므로 사용하지 않는다.
- release는 `crew_participant.released_point_history_id`가 이미 있으면 기존 release 원장을 재사용하고, 없을 때만 현재 cycle의 `CREW_RESERVE_RELEASE`를 append한 뒤 같은 트랜잭션에서 `released_point_history_id`를 연결한다. `CANCELLED → PENDING` reopen 시 이 FK를 `null`로 reset해 다음 cycle release를 허용한다.
- 정산 환급 idempotency key는 runtime-generated `settlement.id`에 의존하지 않는다. crew/participant 자연키와 최종 정산 1회를 뜻하는 `final` suffix를 사용해 같은 participant의 최종 환급 중복 지급을 차단한다.
- 정산 환급의 `settlement_item.id`는 지급 근거 스냅샷 추적용 linkage이며 `reference_id`와 `settlement_item.point_history_id`에 남긴다.
- 동일 `settlement-refund:final` key 재시도는 기존 원장을 재사용/연결한다. 단, 같은 key인데 `settlement_item.id`, 환급 금액, 정산 algorithm version, 인정 성공 수 등 canonical payout input이 다르면 idempotency conflict로 실패해야 한다.
- 추후 재정산/보정 지급이 필요하면 `final`을 재사용하지 않고 별도 transaction type/key(예: `settlement-adjustment:{adjustmentId}`)로 분리한다.

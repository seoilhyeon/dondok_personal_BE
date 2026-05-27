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
| `locked_balance` | `active_locked_amount + settlement_pending_amount` |
| `total_balance` | `available_balance + reserved_balance + locked_balance` |

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
      "reference_id": 3001,
      "created_at": "2026-05-07T09:30:00+09:00"
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

**`reference_type` / `reference_id` / `idempotency_key` 매핑**

| 도메인 동작 | `transaction_type` | `reference_type` | `reference_id` | `idempotency_key` |
|---|---|---|---|---|
| 포인트 충전 | `POINT_CHARGE` | `POINT_CHARGE` | 생성된 `point_history.id` | `charge:{paymentKey}` |
| 크루 참여 보증금 reserve | `CREW_DEPOSIT_RESERVE` | `CREW_PARTICIPANT` | `crew_participant.id` | `crew:{crewId}:participant:{participantId}:reserve:{cycle}` |
| PENDING reserve 반환 | `CREW_RESERVE_RELEASE` | `CREW_PARTICIPANT` | `crew_participant.id` | `crew:{crewId}:participant:{participantId}:reserve-release:{cycle}` |
| 일반 정산 환급 | `CREW_SETTLEMENT_REFUND` | `SETTLEMENT_ITEM` | `settlement_item.id` | `crew:{crewId}:participant:{participantId}:settlement-refund:{settlementId}` |

- `CREW_DEPOSIT_RESERVE`는 자산 이동이 아니라 reserve lock 이벤트다(`available_balance -= deposit_amount` / `reserved_balance += deposit_amount`). 일반 참여자의 `PENDING` 신청과 호스트 auto-participation reserve가 같은 `transaction_type`을 사용한다.
- 승인 시점 `PENDING → LOCKED` 전이는 `reserved_balance → locked_balance` bucket transition만 수행하며 새 `point_history` row를 만들지 않는다.
- `{cycle}`은 `CANCELLED → PENDING` reopen 시 증가하여 이전 사이클과 중복 처리를 방지한다. 최초 생성은 cycle `1`이며, 사이클 numbering은 implementation detail이다.

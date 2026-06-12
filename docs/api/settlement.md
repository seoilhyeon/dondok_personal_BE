# 정산

## `GET /api/crews/{crewId}/settlement`

> 크루의 정산 진행 상태를 조회한다.

**Response** `200 OK`

정산 row가 없는 경우:

```json
{
  "crew_id": 42,
  "settlement_id": null,
  "status": "NONE",
  "retry_count": 0,
  "failure_code": null,
  "failure_message": null,
  "started_at": null,
  "finished_at": null
}
```

정산 row가 있는 경우:

```json
{
  "crew_id": 42,
  "settlement_id": 501,
  "status": "RUNNING",
  "retry_count": 1,
  "failure_code": null,
  "failure_message": null,
  "started_at": "2026-06-01T13:12:10+09:00",
  "finished_at": null
}
```

**정책**

| 필드 | 설명 |
|------|------|
| `settlement_id` | 정산 식별자. `Settlement` row가 없으면 `null` |
| `status` | 정산 처리 상태. `NONE`은 API projection 전용이며 DB에는 저장하지 않는다. `NONE` MUST NOT be persisted in `Settlement.status`. |
| `retry_count` | 재시도 횟수 |
| `failure_code` | 실패 사유 코드. 실패하지 않았으면 `null`. 값 목록: `INPUT_LOAD_FAILED`, `CALCULATION_FAILED`, `POINT_CREDIT_FAILED`, `DUPLICATE_SETTLEMENT`, `LOCK_ACQUIRE_FAILED`, `UNKNOWN` |
| `failure_message` | 실패 상세 메시지 (내부 로그용) |
| `started_at` | 정산 실행 시작 시각 |
| `finished_at` | 정산 성공/실패 종료 시각. 진행 중이면 `null` |

- `NONE`은 API projection이며 `Settlement` row가 아직 없음을 뜻한다. `NONE` MUST NOT be persisted in `Settlement.status`.
- `PENDING → RUNNING → SUCCEEDED / RETRY_WAIT / FAILED`는 `Settlement.status` 원천 상태를 그대로 반영한다.
- `started_at` / `finished_at`은 runtime execution fact다. lifecycle/cutoff authority는 `start_at`, crew timezone, daily cutoff 같은 scheduled semantic anchor에 남는다.

**Error**

- `INVALID_INPUT`
- `CREW_NOT_FOUND`
- `CREW_ACCESS_DENIED`

---

## `GET /api/settlements/{settlementId}`

> 완료된 정산의 상세 결과와 참여자별 환급 내역을 조회한다.

**Response** `200 OK`

```json
{
  "settlement_id": 501,
  "crew_id": 42,
  "status": "SUCCEEDED",
  "retry_count": 1,
  "total_participants": 5,
  "total_locked_amount": 500000,
  "total_recognized_success": 390,
  "total_base_refund_amount": 499996,
  "total_remainder_amount": 4,
  "remainder_policy": "HOST_REMAINDER",
  "failure_code": null,
  "failure_message": null,
  "started_at": "2026-06-01T13:12:10+09:00",
  "finished_at": "2026-06-01T13:12:18+09:00",
  "items": [
    {
      "settlement_item_id": 7001,
      "crew_participant_id": 101,
      "participant_status_snapshot": "LOCKED",
      "deposit_amount": 100000,
      "success_count_raw": 92,
      "recognized_success_count": 90,
      "recognized_dates_count": 30,
      "excluded_success_count": 2,
      "share_ratio": "0.230769",
      "base_refund_amount": 115384,
      "remainder_bonus_amount": 4,
      "refund_amount": 115388,
      "point_history_id": 99001,
      "calculation_reason": {
        "included_dates": ["2026-05-01", "2026-05-02"],
        "excluded_logs": [
          {
            "server_time": "2026-05-02T07:10:11+09:00",
            "code": "DAILY_DUPLICATE"
          }
        ]
      }
    }
  ]
}
```

**Error**

- `INVALID_INPUT`
- `CREW_ACCESS_DENIED`

**헤더 필드 정책**

| 필드 | 설명 |
| --- | --- |
| `total_participants` | 정산 대상 frozen `LOCKED` participant 수 |
| `total_locked_amount` | 정산 시점의 `crew_participant.deposit_amount` 합계 스냅샷. `point_account`/`point_history`를 재합산하지 않는다 |
| `total_recognized_success` | 전체 참여자의 인정 성공 수 합계 |
| `total_base_refund_amount` | floor 연산 후 전체 기본 환급 합계 |
| `total_remainder_amount` | floor 연산 후 남은 잔액 |
| `remainder_policy` | 잔액 처리 정책. MVP는 `HOST_REMAINDER`: floor 연산 후 남은 잔액 전액을 방장(호스트)의 `crew_participant`에 추가 지급한다 |

**정산 항목(item) 필드 정책**

| 필드 | 설명 |
| --- | --- |
| `participant_status_snapshot` | 정산 시점의 참여 상태 스냅샷. 정산 후 row가 변경되어도 이 값이 계산 기준 |
| `deposit_amount` | 해당 참여자의 보증금 |
| `success_count_raw` | 중복/비유효 포함 raw 성공 로그 수 |
| `recognized_success_count` | 중복·비유효 제외 후 정산에서 실제 인정된 성공 수 |
| `recognized_dates_count` | 인정된 날짜 수 |
| `excluded_success_count` | 중복 등 제외된 성공 수 (`success_count_raw - recognized_success_count`) |
| `share_ratio` | 전체 인정 성공 중 해당 참여자 비율. 소수점 정밀도 오해 방지를 위해 string decimal로 반환 |
| `base_refund_amount` | `FLOOR(total_locked_amount × share_ratio)` |
| `remainder_bonus_amount` | `HOST_REMAINDER` 정책에서 방장(호스트)에게 추가 지급되는 잔액 전액. 방장이 아닌 참여자는 `0` |
| `refund_amount` | 실제 환급된 금액 (`base_refund_amount + remainder_bonus_amount`). **persisted 최종 환급 source of truth** |
| `point_history_id` | 연결된 포인트 원장 ID. `null`이면 아직 지급 미완료 상태 |
| `calculation_reason` | 정산 포함/제외 근거. `includedDates`(인정된 날짜 목록)와 `excludedLogs`(제외된 로그의 `serverTime` + 제외 `code`) |

**`final_rank` (응답 포함 시)**

- 저장 컬럼이 아닌 logical projection이다.
- `recognized_success_count DESC`, 동률이면 `crew_participant_id ASC` 기준 read-time 계산한다.
- payout authority가 아니며 지급 결과 변경에 사용하지 않는다.

**정책**

- `settlement_item`은 참여자별 계산 스냅샷의 source of truth다.
- `point_history`는 실제 잔액에 반영된 금액 source of truth다.
- `SUCCEEDED`는 모든 `settlement_item.point_history_id`가 채워지고 대응 `point_history` 존재가 검증된 상태다.
- partial 상태에서는 일부 item의 `point_history_id`가 `null`일 수 있으며, 이 경우 `status`는 `SUCCEEDED`가 아니라 `RETRY_WAIT` 또는 `FAILED`다.
- `SUCCEEDED` 이후 운영/분쟁/조회 기준은 `settlement_item + point_history`다. `MissionLog` 기반 replay는 감사/디버깅용 검증에만 사용하며 지급 결과를 변경하지 않는다.
- 전체 인정 성공이 `0`이면 all-fail equal-principal refund를 적용한다:
  - 모든 참여자의 `refund_amount = deposit_amount` (보증금 원금 그대로 환급).
  - `remainder_bonus_amount = 0`이며 추가 지급 없음.
- `HOST_REMAINDER` 정책에서 `total_remainder_amount > 0`이면 전액이 방장의 `refund_amount`에 합산된다.

---

## Admin Settlement API

관리자 정산 실행 API는 MVP active contract에서 제외한다(deferred).
---

## BATCH-000 계약 정렬 노트

정산 배치 구현을 진행할 때 이 active API 문서는 백엔드 문서 세트 안에서 아래 기준으로 해석한다.

- 공개 정산 상태값은 `NONE`, `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`, `RETRY_WAIT`로 고정한다. 이 중 `NONE`은 API 응답 전용 projection이며 DB에 저장하는 상태가 아니다. `NONE` MUST NOT be persisted in `Settlement.status`.
- 정산 실패 코드는 `INPUT_LOAD_FAILED`, `CALCULATION_FAILED`, `POINT_CREDIT_FAILED`, `DUPLICATE_SETTLEMENT`, `LOCK_ACQUIRE_FAILED`, `UNKNOWN`으로 고정한다.
- active 정산 상세 응답에서 절사 잔액은 header의 `remainder_policy`와 item별 `remainder_bonus_amount`로 표현한다.
- `remainder_winner_*` 응답 필드는 추후 API/product 계약 변경이 명시되기 전까지 추가하지 않는다.
- BATCH-000에서 발견한 FE/source 불일치는 이 docs-only 작업 범위에서 구현하지 않고 후속 작업으로 기록한다.

백엔드 구현 handoff는 같은 백엔드 문서 세트의 `../design/settlement-batch-contract.md`를 참고한다. 해당 문서에 BATCH-000 백엔드 계약 정렬 결과, drift 목록, BATCH-005/BATCH-006 연계 메모가 정리되어 있다.

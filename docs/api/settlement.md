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
  "crew_name": "아침 갓생 30일",
  "crew_started_at": "2026-05-01",
  "crew_ended_at": "2026-05-30",
  "mission_days": 30,
  "crew_success_rate": "0.6500",
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
  "my_rank": 1,
  "items": [
    {
      "settlement_item_id": 7001,
      "crew_participant_id": 101,
      "nickname": "갓생러",
      "is_me": true,
      "participant_status_snapshot": "LOCKED",
      "deposit_amount": 100000,
      "success_count_raw": 92,
      "recognized_success_count": 90,
      "recognized_dates_count": 30,
      "excluded_success_count": 2,
      "share_ratio": "0.230769",
      "rank": 1,
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
| `crew_name` | 크루 이름 |
| `crew_started_at` | 미션 시작일 (date) |
| `crew_ended_at` | 미션 종료일 (date) |
| `mission_days` | 기간 내 실제 미션 진행일 수. `DAILY`=전체 일수, 요일 지정=스케줄 요일 수 |
| `crew_success_rate` | 크루 전체 성공률 = `total_recognized_success / (total_participants × mission_days)`. string decimal(scale 4), 분모 0이면 `"0"` |
| `my_rank` | 인증 사용자의 최종 순위. 참여 row가 없으면 `null` |
| `total_participants` | 정산 시점의 frozen `LOCKED` participant 수 |
| `total_locked_amount` | 정산 시점의 `crew_participant.deposit_amount` 합계 스냅샷 |
| `total_recognized_success` | 전체 참여자의 정산 인정 성공 수 합계 |
| `total_base_refund_amount` | floor 계산 후 전체 기본 환급 합계 |
| `total_remainder_amount` | floor 계산 후 남은 잔여 금액 |
| `remainder_policy` | 잔여금 처리 정책. MVP active 값은 `HOST_REMAINDER`다. |

**정산 항목(item) 필드 정책**

| 필드 | 설명 |
| --- | --- |
| `settlement_item_id` | 정산 항목 식별자 |
| `crew_participant_id` | 정산 당시 참여자 식별자 |
| `nickname` | 참여자 닉네임 |
| `is_me` | 인증 사용자 본인 행 여부 |
| `participant_status_snapshot` | 정산 시점의 참여 상태 스냅샷 |
| `deposit_amount` | 해당 참여자의 보증금 |
| `success_count_raw` | 중복/비유효 포함 raw 성공 로그 수 |
| `recognized_success_count` | 중복/비유효 제외 후 정산에서 인정한 성공 수 |
| `recognized_dates_count` | 인정된 날짜 수 |
| `excluded_success_count` | 제외된 성공 수 (`success_count_raw - recognized_success_count`) |
| `share_ratio` | 전체 인정 성공 중 해당 참여자 비율. scale 6 string decimal |
| `rank` | 최종 순위. `share_ratio DESC`, 동률이면 `crew_participant_id ASC`, 공동 순위(예: `1, 2, 2`) |
| `base_refund_amount` | 기본 환급액 |
| `remainder_bonus_amount` | 잔여금 정책에 따라 추가 지급된 금액. 일반 참여자는 `0` |
| `refund_amount` | 실제 최종 환급액 (`base_refund_amount + remainder_bonus_amount`) |
| `point_history_id` | 연결된 포인트 원장 ID. `null`이면 아직 지급 미완료 상태 |
| `calculation_reason` | 정산 포함/제외 근거 JSON object |

**정책**

- 이 endpoint는 전체 상세/운영 확인용이며 `items[]`를 반환한다.
- `settlement_item`은 참여자별 최종 계산 결과의 source of truth다.
- `point_history`는 실제 금액 지급 원장의 source of truth다.
- `SUCCEEDED` 이후 운영/분쟁/조회 기준은 `settlement_item + point_history`이며, MissionLog replay는 감사/디버깅 검증에만 사용한다.
- `items[]`는 `settlement_item.id ASC`로 안정 정렬한다.
- `member_id`, `member_uuid`, 멤버 내부 식별자는 item 응답에 포함하지 않는다. 여기서 멤버 내부 식별자는 `settlement_item_id`, `crew_participant_id` 같은 정산/참여 row 식별자를 의미하지 않는다.

---

## `GET /api/settlements/{settlementId}/me`

> 인증 사용자의 본인 정산 결과를 조회한다. 최종 정산 완료 UI에서 개인 환급액을 표시할 때 사용한다.

**Response** `200 OK`

```json
{
  "settlement_id": 501,
  "crew_id": 42,
  "crew_name": "아침 갓생 30일",
  "crew_started_at": "2026-05-01",
  "crew_ended_at": "2026-05-30",
  "status": "SUCCEEDED",
  "retry_count": 1,
  "failure_code": null,
  "failure_message": null,
  "started_at": "2026-06-01T13:12:10+09:00",
  "finished_at": "2026-06-01T13:12:18+09:00",
  "my_item": {
    "settlement_item_id": 7001,
    "crew_participant_id": 101,
    "nickname": null,
    "is_me": true,
    "participant_status_snapshot": "LOCKED",
    "deposit_amount": 100000,
    "success_count_raw": 92,
    "recognized_success_count": 90,
    "recognized_dates_count": 30,
    "excluded_success_count": 2,
    "share_ratio": "0.230769",
    "rank": null,
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
}
```

**Error**

- `INVALID_INPUT`
- `CREW_ACCESS_DENIED`

**정책**

- `crew_name`, `crew_started_at`, `crew_ended_at`는 정산 완료 모달 표시용 크루 정보다.
- `my_item`은 인증 사용자의 본인 `settlement_item` projection이다.
- `my_item.refund_amount`는 인증 사용자의 최종 환급액 source of truth다.
- `my_item.is_me`는 항상 `true`다. `my_item.nickname`/`my_item.rank`는 `/me`에서 산출하지 않아 `null`이며, 전체 순위/명단이 필요하면 `GET /api/settlements/{settlementId}`를 사용한다.
- 서버가 `settlement_id + member_uuid`로 bounded 조회해 `my_item`을 선택한다. 클라이언트가 전체 `items[]`에서 본인 row를 추론하지 않는다.
- 이 endpoint는 `items[]`를 반환하지 않는다.
- 인증 사용자가 접근 가능한 호스트지만 대응되는 `settlement_item` row가 없는 brownfield/legacy 케이스에서는 `my_item`이 `null`일 수 있다.
- `member_id`, `member_uuid`, 멤버 내부 식별자는 item 응답에 포함하지 않는다. 여기서 멤버 내부 식별자는 `settlement_item_id`, `crew_participant_id` 같은 정산/참여 row 식별자를 의미하지 않는다.

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

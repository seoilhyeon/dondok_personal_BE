# 대시보드

## `GET /api/dashboard`

> 내가 참여 중인 전체 크루의 집계 대시보드를 조회한다.

**Response** `200 OK`

```json
{
  "total_expected_refund_amount": 57260,
  "today_delta_amount": 960,
  "today_delta_ratio": "0.017",
  "rising_crew_count": 3,
  "falling_crew_count": 1,
  "max_delta_crew": {
    "crew_id": 10,
    "crew_name": "아침 6시 기상",
    "today_delta_amount": 1200
  },
  "crews": [
    {
      "crew_id": 10,
      "crew_name": "아침 6시 기상",
      "share_ratio": "0.41",
      "expected_refund_amount": 23500,
      "today_delta_amount": 1200
    },
    {
      "crew_id": 11,
      "crew_name": "홀트 30분",
      "share_ratio": "0.25",
      "expected_refund_amount": 14160,
      "today_delta_amount": -840
    }
  ]
}
```

**Error**

- `PARTICIPANT_NOT_FOUND`

**응답 필드 설명**

| 필드 | 설명 |
|------|------|
| `total_expected_refund_amount` | 참여 중인 전체 크루의 예상 환급금 합계 |
| `today_delta_amount` | 직전 일일 정산 배치 대비 전체 예상 환급금 변동액. 음수 가능 |
| `today_delta_ratio` | `today_delta_amount / 직전 일일 정산 배치 기준 total_expected_refund_amount`. string decimal |
| `rising_crew_count` | `today_delta_amount`가 양수인 크루 수 |
| `falling_crew_count` | `today_delta_amount`가 음수인 크루 수 |
| `max_delta_crew` | 오늘 절댓값 기준 변동액이 가장 큰 크루. 동률이면 `crew_id ASC` 기준 |
| `max_delta_crew.crew_id` | 최대 변동 크루 ID |
| `max_delta_crew.crew_name` | 최대 변동 크루 이름 |
| `max_delta_crew.today_delta_amount` | 최대 변동 크루의 변동액. 음수 가능 |
| `crews` | 참여 중인 크루 목록 |
| `crews[].crew_id` | 크루 ID |
| `crews[].crew_name` | 크루 이름 |
| `crews[].share_ratio` | 해당 크루 내 나의 지분율. 직전 일일 정산 배치 기준 확정값. string decimal |
| `crews[].expected_refund_amount` | 해당 크루의 예상 환급금. 직전 일일 정산 배치 기준 확정값 |
| `crews[].today_delta_amount` | 해당 크루의 직전 일일 정산 배치 대비 변동액. 음수 가능 |

**정책**

- `today_delta_amount`의 기준 시각은 해당 크루의 `DailySettlementType`별 `autoCertificationAt` 시점에 실행된 직전 정산 배치 결과 기준이다. 크루마다 기준 시각이 다를 수 있다.
- 직전 정산 배치가 아직 없는 크루(배치가 한 번도 실행되지 않은 경우)는 `today_delta_amount`를 `null`로 반환한다.
- 참여 크루가 없으면 `crews`는 빈 배열, `total_expected_refund_amount`·`today_delta_amount`·`today_delta_ratio`·`rising_crew_count`·`falling_crew_count`는 `0`, `max_delta_crew`는 `null`로 반환한다.
- 크루 상태가 정산 배치 결과를 제공할 수 없는 경우(`RECRUITING` 등 배치 미실행 상태) 해당 크루의 `expected_refund_amount`·`today_delta_amount`·`share_ratio`는 `null`로 반환한다.
- 적용 불가 필드는 생략하지 않고 `null`로 반환한다.

---

## `GET /api/dashboard/crews/{crewId}`

> 특정 크루에서의 나의 대시보드 상세를 조회한다.

**Response** `200 OK`

```json
{
  "crew_id": 42,
  "crew_name": "아침 갓생 30일",
  "crew_participant_id": 101,
  "settlement_id": null,
  "crew_status": "ACTIVE",
  "settlement_status": "NONE",
  "projection_status": "LIVE",
  "projection_notice": "ESTIMATED_NOT_FINAL",
  "days_until_end": 2,
  "my_deposit_amount": 100000,
  "my_success_count": 5,
  "my_expected_refund_amount": 103226,
  "my_expected_refund_delta_amount": 3226,
  "rank": 2,
  "rank_total": 5,
  "rank_delta": 1,
  "next_settlement_at": "2026-05-21T12:00:00+09:00",
  "participants": [
    {
      "crew_participant_id": 101,
      "nickname": "갓생러",
      "success_count": 5,
      "share_ratio": "0.235",
      "is_me": true
    },
    {
      "crew_participant_id": 102,
      "nickname": "아침형인간",
      "success_count": 4,
      "share_ratio": "0.221",
      "is_me": false
    }
  ],
  "updated_at": "2026-05-21T12:00:00+09:00"
}
```

**Error**

- `CREW_NOT_FOUND`
- `PARTICIPANT_NOT_FOUND`
- `CREW_ACCESS_DENIED`

**ProjectionStatus**

| 값 | 설명 |
|----|------|
| `NOT_STARTED` | `RECRUITING` 등 정산 배치가 한 번도 실행되지 않은 상태 |
| `LIVE` | `ACTIVE` 상태에서 직전 정산 배치 결과 기준 값을 계산했다 |
| `CLOSED_ESTIMATE` | `CLOSED` 상태에서 직전 정산 배치 결과 기준 값을 계산한다. 최종값이 아니다 |
| `NOT_PROVIDED` | `CANCELLED` 등 projection을 제공하지 않는 상태. 환급/정산 안내는 Settlement API 기준이다 |
| `SETTLEMENT_SUCCEEDED` | 최종 정산이 성공했다. Dashboard는 최종값을 복제하지 않고 `settlement_id`로 Settlement API 조회를 유도한다 |

**ProjectionNotice**

| 값 | 설명 |
|----|------|
| `ESTIMATED_NOT_FINAL` | 현재 값은 직전 정산 배치 기준 참고용 값이며 최종 정산 결과가 아니다 |
| `NOT_STARTED` | 정산 배치가 아직 실행되지 않아 성과/보상 projection이 시작되지 않았다 |
| `NOT_PROVIDED` | 현재 크루 상태에서는 Dashboard projection을 제공하지 않는다 |
| `SETTLEMENT_RESULT_AVAILABLE` | 최종 정산 결과가 존재하므로 `settlement_id`로 Settlement API를 조회해야 한다 |
| `INSUFFICIENT_PROJECTION_INPUT` | projection 계산에 필요한 참여자/보증금 입력을 충분히 확정할 수 없어 일부 필드를 `null`로 반환한다 |

**상태별 필드 계약**

| `projection_status` | `settlement_id` | `my_success_count` | 계산 필드들 (`share_ratio`, `expected_refund`, `rank`) | `updated_at` |
|---|---|---|---|---|
| `NOT_STARTED` | `null` | `0` | 모두 `null` | value |
| `LIVE` | nullable | value | value 또는 `null` | value |
| `CLOSED_ESTIMATE` | nullable | value | value 또는 `null` | value |
| `NOT_PROVIDED` | nullable | `null` | 모두 `null` | value |
| `SETTLEMENT_SUCCEEDED` | value | `null` | 모두 `null` | value |

- `LIVE` / `CLOSED_ESTIMATE`에서 denominator 등 필수 projection 입력이 부족하면 해당 필드는 `null`이고 `projection_notice = INSUFFICIENT_PROJECTION_INPUT`을 사용한다.
- `SETTLEMENT_SUCCEEDED`에서 계산 필드를 `null`로 내려주는 이유는 데이터가 없어서가 아니라 최종값의 source of truth가 `GET /api/settlements/{settlementId}`이기 때문이다.

**응답 필드 설명**

| 필드 | 설명 |
|------|------|
| `crew_id` | 크루 ID |
| `crew_name` | 크루 이름 |
| `crew_participant_id` | 나의 크루 참여자 ID |
| `settlement_id` | 최종 정산 ID. `SETTLEMENT_SUCCEEDED` 이전은 `null` |
| `crew_status` | 현재 크루 상태 |
| `settlement_status` | 정산 상태. `NONE`은 해당 크루의 Settlement row가 아직 없음을 의미하며 projection 계산 불가를 의미하지 않는다 |
| `projection_status` | Dashboard 값의 현재 산출 상태. API 응답용 값이며 DB에 저장하지 않는다 |
| `projection_notice` | Dashboard 값에 대한 안내 메시지 식별자. API 응답용 값이며 DB에 저장하지 않는다 |
| `days_until_end` | 미션 종료일까지 남은 일수. 종료일 당일은 `0`, 종료일 이후는 `null` |
| `my_deposit_amount` | 나의 보증금 원금 |
| `my_success_count` | 직전 정산 배치 기준 나의 확정 성공 횟수 |
| `my_expected_refund_amount` | 직전 정산 배치 기준 나의 예상 환급금. `FLOOR(total_locked_amount × my_share_ratio)`. 전체 성공 횟수가 0인 경우 `my_deposit_amount` 반환 |
| `my_expected_refund_delta_amount` | 현재 배치 기준 `my_expected_refund_amount - 직전 배치 기준 my_expected_refund_amount`. 직전 배치가 없는 경우 `null`. 음수 가능 |
| `rank` | 직전 정산 배치 기준 나의 순위. `success_count DESC`, 동률이면 `crew_participant_id ASC` |
| `rank_total` | 전체 참여자 수 |
| `rank_delta` | 직전 정산 배치 대비 순위 변동. 양수면 상승, 음수면 하락, 0이면 유지 |
| `next_settlement_at` | 다음 정산 배치 예정 시각 (`autoCertificationAt` 기준). 크루가 종료되었거나 산출 불가한 경우 `null` |
| `participants` | 크루 전체 참여자 목록 |
| `participants[].crew_participant_id` | 참여자 ID |
| `participants[].nickname` | 참여자 닉네임. 본인 포함 전원 실제 닉네임 표시 |
| `participants[].success_count` | 직전 정산 배치 기준 해당 참여자의 확정 성공 횟수 |
| `participants[].share_ratio` | 직전 정산 배치 기준 해당 참여자의 지분율. string decimal |
| `participants[].is_me` | 본인 여부 |
| `updated_at` | Dashboard 응답을 생성한 시각. 가장 최근 정산 배치 실행 시각과 동일하다 |

**지분율 및 예상 환급금 계산 기준**

- 분모: 직전 정산 배치 기준 크루원 전체 확정 성공 횟수 합계
- 분자: 직전 정산 배치 기준 나의 확정 성공 횟수
- `my_share_ratio = my_success_count / total_success_count`
- `my_expected_refund_amount = FLOOR(total_locked_amount × my_share_ratio)`
- 전체 성공 횟수가 0인 경우 `my_expected_refund_amount = my_deposit_amount`

**정책**

- Dashboard의 모든 값은 해당 크루의 `DailySettlementType`별 `autoCertificationAt` 시점에 실행된 직전 정산 배치 결과를 기준으로 한다. 배치 실행 전 제출된 인증 로그는 반영되지 않는다.
- Dashboard는 `Settlement.status = SUCCEEDED` 전까지 최종 정산 결과가 아니며, 정산 source of truth가 아니다.
- Dashboard projection과 최종 settlement 결과가 달라도 시스템 오류로 간주하지 않는다.
- `my_share_ratio`와 `participants[].share_ratio`는 소수 오해 방지를 위해 string decimal로 반환한다.
- 적용 불가 필드는 생략하지 않고 `null`로 반환한다.
- Dashboard는 정산의 `remainder`, `remainder_policy`, deterministic remainder allocation, 1원 단위 잔액 처리를 계산하거나 반영하지 않는다. 해당 최종 지급 차이는 Settlement API에서만 확인한다.
- `SETTLEMENT_SUCCEEDED` 이후 최종 성공 횟수, 최종 환급금, 최종 지분율은 `GET /api/settlements/{settlementId}`의 `settlement_item` 기준으로 확인한다.

**`locked_balance`와의 관계**

- `GET /api/points`의 `locked_balance`는 계정 단위 현재 잠긴 보증금 UX projection이다.
- Dashboard의 `my_expected_refund_amount`는 특정 crew/crew_participant 기준 예상 환급금 projection이다.
- FE는 `locked_balance`, `available_balance`, `my_expected_refund_amount`를 합산하거나 차감해 최종 보유 포인트, 출금 가능 금액, 최종 정산 후 확정 환급금을 계산하면 안 된다.
- 최종 환급 여부와 금액은 `Settlement.status = SUCCEEDED` 이후 Settlement API와 `point_history` 원장 기준이다.

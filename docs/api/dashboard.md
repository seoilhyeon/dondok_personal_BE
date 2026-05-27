# 대시보드

## `GET /api/crews/{crewId}/dashboard`

> 내 보증금 현황, 예상 환급액, 랭킹 등 크루 대시보드 정보를 조회한다.

**Response** `200 OK`

```json
{
  "crew_id": 42,
  "crew_participant_id": 101,
  "settlement_id": null,
  "crew_status": "ACTIVE",
  "settlement_status": "NONE",
  "projection_status": "LIVE",
  "projection_notice": "ESTIMATED_NOT_FINAL",
  "my_deposit_amount": 100000,
  "my_success_count": 5,
  "my_recognized_success_count_estimated": 4,
  "total_recognized_success_count_estimated": 31,
  "my_share_ratio_estimated": "0.12903200",
  "my_expected_refund_amount": 103226,
  "my_expected_refund_delta_amount": 3226,
  "rank_estimated": 3,
  "updated_at": "2026-05-11T00:00:00+09:00"
}
```

**Error**

- `CREW_NOT_FOUND`
- `PARTICIPANT_NOT_FOUND`
- `CREW_ACCESS_DENIED`

**ProjectionStatus**

| 값 | 설명 |
|----|------|
| `NOT_STARTED` | `RECRUITING` 등 미션 수행 전 상태라 진행/환급 projection이 아직 시작되지 않았다 |
| `LIVE` | `ACTIVE` 상태에서 현재 `MissionLog`와 참여자 상태를 기준으로 current-basis estimate를 계산했다 |
| `CLOSED_ESTIMATE` | `CLOSED` 상태에서 매 요청 시 현재 확인 가능한 입력으로 current-basis estimate를 계산한다. 저장된 dashboard snapshot이 아니며 최종값도 아니다 |
| `NOT_PROVIDED` | `CANCELLED` 등 수행 projection을 제공하지 않는 상태다. 환급/정산 안내는 Settlement API 기준이다 |
| `SETTLEMENT_SUCCEEDED` | 최종 정산이 성공했다. Dashboard는 최종값을 복제하지 않고 `settlement_id`로 Settlement API 조회를 유도한다 |

**ProjectionNotice**

| 값 | 설명 |
|----|------|
| `ESTIMATED_NOT_FINAL` | 현재 값은 참고용 current-basis estimate이며 최종 정산 결과가 아니다 |
| `NOT_STARTED` | 미션 수행 전이라 성과/보상 projection이 아직 시작되지 않았다 |
| `NOT_PROVIDED` | 현재 방 상태에서는 Dashboard 진행/환급 projection을 제공하지 않는다 |
| `SETTLEMENT_RESULT_AVAILABLE` | 최종 정산 결과가 존재하므로 `settlement_id`로 Settlement API를 조회해야 한다 |
| `INSUFFICIENT_PROJECTION_INPUT` | projection 계산에 필요한 참여자/보증금 입력을 충분히 확정할 수 없어 일부 추정 필드를 `null`로 반환한다 |

**상태별 필드 계약**

| `projection_status` | `settlement_id` | `my_success_count` | 추정 필드들 (`my_recognized_*`, `share_ratio`, `refund`, `rank`) | `updated_at` |
|---|---|---|---|---|
| `NOT_STARTED` | `null` | `0` | 모두 `null` | value |
| `LIVE` | nullable | value | value 또는 `null` | value |
| `CLOSED_ESTIMATE` | nullable | value | value 또는 `null` | value |
| `NOT_PROVIDED` | nullable | `null` | 모두 `null` | value |
| `SETTLEMENT_SUCCEEDED` | value | `null` | 모두 `null` | value |

- `LIVE` / `CLOSED_ESTIMATE`에서 denominator 등 필수 projection 입력이 부족하면 해당 추정 필드는 `null`이고 `projection_notice = INSUFFICIENT_PROJECTION_INPUT`을 사용한다.
- `SETTLEMENT_SUCCEEDED`에서 추정 필드를 `null`로 내려주는 이유는 데이터가 없어서가 아니라 최종값의 source of truth가 `GET /api/settlements/{settlementId}`이기 때문이다.

**응답 필드 설명**

| 필드 | 설명 |
|------|------|
| `my_success_count` | latest/effective slot summary 기준의 현재 성공 표시 수. 일반 feed item 수나 정산 인정 성공 수가 아니다 |
| `my_recognized_success_count_estimated` | 현재 시점에서 정산 규칙을 가능한 범위로 반영한 추정 인정 성공 수 |
| `total_recognized_success_count_estimated` | 참여자별 추정 인정 성공 수 합계 |
| `my_share_ratio_estimated` | 전체 인정 성공 중 내 비율. 소수점 정밀도 오해 방지를 위해 string decimal로 반환 |
| `my_expected_refund_amount` | `total_recognized_success_count_estimated > 0`이면 `FLOOR(total_locked_amount × my_share_ratio_estimated)`. 0인 경우 all-fail equal-principal refund로 `my_deposit_amount` 반환 |
| `my_expected_refund_delta_amount` | `my_expected_refund_amount - my_deposit_amount`. 수익 확정값이 아닌 현재 기준 설명용 차이값 |
| `rank_estimated` | 추정 수행/참여도 표시 순서. `recognized_success_count_estimated DESC`, 동률이면 `crew_participant_id ASC` 기준 |
| `updated_at` | Dashboard 응답을 생성한 시각. 참여자 상태 변경 시각이나 최근 미션 로그 제출 시각이 아니다 |

**Projection source 역할**

| Source | Dashboard에서의 역할 |
|--------|---------------------|
| `mission_log` | 성공 후보와 수행 현황의 primary event source. `certification_status = 'SUCCESS'` 로그만 후보로 사용하고, 인정 판단 시간은 `MissionLog.server_time` 기준이다 |
| `crew_participant` | 참여자 식별, frozen `LOCKED` baseline, `deposit_amount` 보증금 금액 source다 |
| `crew` | 방 상태, 기간, 미션 주기/규칙 컨텍스트다. 총 보증금 source가 아니다 |
| `settlement` | `SUCCEEDED` 여부와 최종값 전환 판단용이다. `SUCCEEDED` 전 Dashboard projection 계산 source가 아니다 |
| `point_history` | Dashboard projection 계산 source가 아니다. 최종 환급/잔액은 Settlement API와 `point_history` 기준이다 |

**`locked_balance`와의 관계**

- `GET /api/points`의 `locked_balance`는 계정 단위 현재 잠긴 보증금 UX projection이다.
- Dashboard의 `my_expected_refund_amount`는 특정 crew/crew_participant 기준 예상 환급금 projection이다.
- FE는 `locked_balance`, `available_balance`, `my_expected_refund_amount`를 합산하거나 차감해 최종 보유 포인트, 출금 가능 금액, 최종 정산 후 확정 환급금을 계산하면 안 된다.
- 최종 환급 여부와 금액은 `Settlement.status = SUCCEEDED` 이후 Settlement API와 `point_history` 원장 기준이다.

**정책**

- Dashboard는 `Settlement.status = SUCCEEDED` 전까지 최종 정산 결과가 아니며, 정산 source of truth가 아니다.
- Dashboard projection과 최종 settlement 결과가 달라도 시스템 오류로 간주하지 않는다.
- `projection_status`, `projection_notice`는 API 응답용 값이며 DB enum이나 도메인 상태 원천으로 저장하지 않는다.
- `settlement_status = NONE`은 해당 방의 `Settlement` row가 아직 없다는 뜻이며, Dashboard projection을 계산할 수 없다는 의미가 아니다.
- `my_share_ratio_estimated`는 소수 오해 방지를 위해 string decimal로 반환한다.
- 적용 불가 필드는 생략하지 않고 `null`로 반환한다.
- Dashboard는 정산의 `remainder`, `remainder_policy`, deterministic remainder allocation, 1원 단위 잔액 처리를 계산하거나 반영하지 않는다. 해당 최종 지급 차이는 Settlement API에서만 확인한다.
- `SETTLEMENT_SUCCEEDED` 이후 최종 인정 성공 횟수, 최종 환급금, 최종 지분율은 `GET /api/settlements/{settlementId}`의 `settlement_item` 기준으로 확인한다.

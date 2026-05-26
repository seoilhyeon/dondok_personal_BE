# 대시보드

## `GET /api/crews/{crewId}/dashboard`

내 보증금 현황, 예상 환급액, 랭킹 등 크루 대시보드 정보를 조회한다.

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
| `NOT_STARTED` | 미션 수행 전 |
| `LIVE` | 진행 중, current-basis 추정 |
| `CLOSED_ESTIMATE` | 종료 후 정산 전, current-basis 추정 |
| `NOT_PROVIDED` | 취소 등 projection 미제공 상태 |
| `SETTLEMENT_SUCCEEDED` | 최종 정산 완료. `settlement_id`로 Settlement API 조회 필요 |

**ProjectionNotice**

| 값 | 설명 |
|----|------|
| `ESTIMATED_NOT_FINAL` | 참고용 추정값. 최종 정산 결과 아님 |
| `NOT_STARTED` | 미션 수행 전 |
| `NOT_PROVIDED` | projection 미제공 |
| `SETTLEMENT_RESULT_AVAILABLE` | `settlement_id`로 Settlement API 조회 필요 |
| `INSUFFICIENT_PROJECTION_INPUT` | 입력 부족으로 일부 추정 필드 `null` |

**정책**

- 모든 금액/비율/순위 필드는 "현재 기준 추정값"이며 최종 정산 전 변동될 수 있다.
- `SETTLEMENT_SUCCEEDED` 이후 최종값은 `GET /api/settlements/{settlementId}` 기준이다.
- `my_share_ratio_estimated`는 소수 오해 방지를 위해 string decimal로 반환한다.
- 적용 불가 필드는 `null`로 반환한다.

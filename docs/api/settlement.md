# 정산

## `GET /api/crews/{crewId}/settlement`

크루의 정산 진행 상태를 조회한다.

**Response** `200 OK`

정산 row가 없는 경우:

```json
{
  "crew_id": 42,
  "settlement_id": null,
  "settlement_type": null,
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
  "settlement_type": "NORMAL",
  "status": "RUNNING",
  "retry_count": 1,
  "failure_code": null,
  "failure_message": null,
  "started_at": "2026-06-01T13:12:10+09:00",
  "finished_at": null
}
```

**Error**

- `CREW_NOT_FOUND`

---

## `GET /api/settlements/{settlementId}`

완료된 정산의 상세 결과와 참여자별 환급 내역을 조회한다.

**Response** `200 OK`

```json
{
  "settlement_id": 501,
  "crew_id": 42,
  "settlement_type": "NORMAL",
  "status": "SUCCEEDED",
  "retry_count": 1,
  "total_participants": 5,
  "total_locked_amount": 500000,
  "total_recognized_success": 390,
  "total_base_refund_amount": 499996,
  "total_remainder_amount": 4,
  "remainder_policy": "DETERMINISTIC_REMAINDER_ALLOCATION",
  "remainder_winner_crew_participant_id": null,
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
      "withdrawn_at_snapshot": null,
      "share_ratio": "0.23076923",
      "base_refund_amount": 115384,
      "remainder_bonus_amount": 4,
      "reward_amount": 15388,
      "refund_amount": 115388,
      "final_amount": 115388,
      "point_history_id": 99001,
      "calculation_reason": {
        "includedDates": ["2026-05-01", "2026-05-02"],
        "excludedLogs": [
          {
            "serverTime": "2026-05-02T07:10:11+09:00",
            "code": "DAILY_DUPLICATE"
          }
        ]
      }
    }
  ]
}
```

**Error**

- `SETTLEMENT_NOT_FOUND`

**정책**

- `settlement_item`은 참여자별 계산 스냅샷이며 정산 결과의 source of truth다.
- `SUCCEEDED`는 모든 `settlement_item.point_history_id` 연결이 완료된 상태다.
- 전체 인정 성공이 0이면 all-fail equal-principal refund를 적용한다.

# AI

## `POST /api/ai/mission-recommendations`

> 목표 텍스트를 기반으로 AI가 미션 설정 초안을 추천한다.

**인증**

`Authorization: Bearer {access_token}` 필수

**Request**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `seed_text` | `string` | Y | 목표/습관 설명 |

**Response** `200 OK`

```json
{
  "draft": {
    "title": "아침 20분 독서 인증",
    "description": "매일 아침 독서한 책 페이지를 사진으로 인증합니다.",
    "frequency_type": "DAILY",
    "mission_schedule_days": [],
    "daily_settlement_type": "A",
    "deposit_amount": 50000,
    "duration_days": 30
  },
  "validation_warnings": [
    {
      "field": "deposit_amount",
      "message": "권장 보증금은 1,000원 단위로 조정되었습니다."
    }
  ]
}
```

**Error**

- `AI_RECOMMENDATION_FAILED`
- `AI_RESPONSE_INVALID`
- `VALIDATION_ERROR`

**정책**

- 추천 결과는 draft이며 자동 저장되지 않는다. 사용자 확인 후 `POST /api/crews`로 별도 저장한다.
- AI 실패는 시스템 실패가 아니다. FE는 기존 입력값을 유지하고 수동 생성 흐름을 제공해야 한다.

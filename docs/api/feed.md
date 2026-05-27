# 피드 / 리액션

## `GET /api/crews/{crewId}/feed`

> 크루 피드(인증 사진 목록)와 일별 미션 현황을 조회한다.

**Query**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `limit` | `integer` | N | feed_items 페이지 크기. 기본 20 |
| `cursor` | `string` | N | 페이지네이션 커서 |
| `status` | `string` | N | `PENDING_REVIEW`, `SUCCESS`, `FAILED` 필터. 생략 시 전체 |
| `from` | `string` | N | 파생 상태 조회 시작일 `YYYY-MM-DD` |
| `to` | `string` | N | 파생 상태 조회 종료일 `YYYY-MM-DD` |

**Response** `200 OK`

```json
{
  "crew_id": 42,
  "feed_items": [
    {
      "mission_log_id": 9001,
      "crew_participant_id": 101,
      "member_uuid": "018f4fd2-6d7a-7a41-9f58-6d07f5c3c907",
      "nickname": "돈독러",
      "image_url": "https://cdn.example.com/mission/9001.jpg",
      "caption": "오늘도 미션 완료했습니다",
      "server_time": "2026-05-11T05:58:10+09:00",
      "created_at": "2026-05-11T05:58:10+09:00",
      "certification_status": "SUCCESS",
      "reject_reason_code": null,
      "reaction_counts": { "👏": 2, "🔥": 1 },
      "my_reactions": ["👏"]
    }
  ],
  "next_cursor": "2026-05-11T05:58:10+09:00_9001",
  "day_statuses": [
    {
      "date": "2026-05-11",
      "status": "SUCCESS",
      "representative_mission_log_id": 9001
    },
    {
      "date": "2026-05-12",
      "status": "NOT_SUBMITTED",
      "representative_mission_log_id": null
    }
  ],
  "participant_day_slots": [
    {
      "crew_participant_id": 101,
      "member_uuid": "018f4fd2-6d7a-7a41-9f58-6d07f5c3c907",
      "date": "2026-05-11",
      "status": "SUCCESS",
      "representative_mission_log_id": 9001
    }
  ]
}
```

**Error**

- `CREW_NOT_FOUND`
- `CREW_ACCESS_DENIED`
- `INVALID_FEED_STATUS_FILTER`

**정책**

- `feed_items[]`는 실제 `mission_log` row가 있는 인증 활동을 append-only stream으로 노출한다. 기본 status set은 `SUCCESS`, `PENDING_REVIEW`, `FAILED`다. `status` 쿼리 파라미터로 특정 상태만 필터링할 수 있다.
- `day_statuses[]`와 `participant_day_slots[]`는 참여자/일자 표시용 latest/effective summary다. `NOT_SUBMITTED`는 `mission_log` row가 없는 synthetic slot projection이며 feed item이 아니다.
- 같은 참여자/같은 날짜/cadence slot에 여러 `mission_log` row가 생기는 경우는 `FAILED`/`PENDING_REVIEW` 재업로드와 host moderation 상태 전이로 한정된다. 이전 `FAILED`/`PENDING_REVIEW` item도 visible item으로 유지하며, 삭제/overwrite로 정산 입력을 바꾸지 않는다.
- `reject_reason_code`는 호스트 검수 거절 사유이며, 해당 없으면 `null`이다. `reject_memo`는 internal/private context이므로 feed 응답에 포함하지 않는다.
- 참여자/일자 summary 대표 규칙:
    - 성공 로그(`SUCCESS`)가 하나 이상 있으면 `SUCCESS`. 대표 로그는 가장 이른 `server_time`, 동률이면 가장 낮은 `mission_log.id`.
    - 성공 로그가 없고 검수 대기 로그(`PENDING_REVIEW`)가 있으면 `PENDING_REVIEW`.
    - 성공/검수 대기 로그가 없고 실패 로그(`FAILED`)가 하나 이상 있으면 `FAILED`.
    - 어떤 로그도 없으면 `NOT_SUBMITTED`.
- `server_time`은 서버가 인증 요청을 수신한 시각으로 **인증/정산 인정 timing anchor**다. `created_at`은 row 생성/feed 정렬/페이지네이션 보조 시각으로 두 값은 의미 axis가 다르다. 정산 인정 시각 기준은 항상 `MissionLog.server_time`이다.
- `caption`은 피드 표시용이며 단독 인증/정산 기준이 아니다.
- `reaction_counts`는 `mission_log_reaction`에서 파생한다. `mission_log`에 저장 카운터를 두거나 갱신하지 않는다. `reaction_counts`는 emoji token을 key로 하는 동적 map이다.
- MVP에서 리액션은 `certification_status = SUCCESS`인 로그에만 허용한다. `FAILED`/`PENDING_REVIEW` item의 `reaction_counts`는 빈 map, `my_reactions`는 빈 list로 응답한다.
- 피드 성공 여부는 정산 인정 여부를 보장하지 않는다. 정산 포함 여부는 `settlement_item.calculation_reason`이 결정한다.
- 이 API의 상태 projection은 정산 인정 횟수, 환급액, 포인트 잔액, lifecycle status의 기준이 아니다.

---

## `POST /api/mission-logs/{missionLogId}/reactions`

> 미션 인증 로그에 이모지 리액션을 추가한다.

**Request**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `reaction_type` | `string` | Y | emoji token 문자열 |

**Response** `200 OK`

```json
{
  "mission_log_id": 9001,
  "my_reactions": ["👏", "🔥"],
  "reaction_counts": { "👏": 3, "🔥": 1 }
}
```

**Error**

- `MISSION_LOG_NOT_FOUND`
- `REACTION_NOT_ALLOWED`
- `INVALID_REACTION_TYPE`

**정책**

- 리액션 대상은 `certification_status = SUCCESS`인 로그로 제한한다.
- `(mission_log_id, member_id, reaction_type)` 기준 멱등 upsert다. 같은 emoji token이 이미 있으면 동일 token 단위로 멱등 처리하고, 다른 emoji token은 별도 row로 공존할 수 있다.
- `(mission_log_id, member_id, reaction_type)` unique constraint 기반의 DB 레벨 멱등성을 보장해야 한다.
- 동일 `(mission_log_id, member_id, reaction_type)`에 대한 동시 중복 요청은 API 에러가 되어서는 안 되며, 최종 상태는 해당 token 1개 존재로 수렴해야 한다.
- 한 회원이 같은 로그에 여러 emoji token을 남길 수 있으나, 동일 token은 1회만 허용한다.
- FE가 선택한 emoji token을 서버가 다른 값으로 변환하지 않는다. trim 후 blank 거부, `VARCHAR(20)` 길이 검증만 수행한다. NFC/NFD 정규화, variation selector collapsing, ZWJ/skin-tone 동등성 정규화는 MVP에서 적용하지 않는다.
- 리액션 생성/수정은 `mission_log`를 변경하지 않는다.
- 리액션은 정산, 환급, 포인트 원장, 크루/참여/정산 상태 전이에 영향을 주지 않는다.

---

## `DELETE /api/mission-logs/{missionLogId}/reactions/me`

> 내 이모지 리액션을 삭제한다.

**Query**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `reaction_type` | `string` | Y | 삭제할 emoji token (URL encoding 필요) |

**Response** `200 OK`

```json
{
  "mission_log_id": 9001,
  "my_reactions": ["🔥"],
  "reaction_counts": { "👏": 2, "🔥": 1 }
}
```

**Error**

- `MISSION_LOG_NOT_FOUND`
- `REACTION_NOT_ALLOWED`

**정책**

- 리액션이 이미 없어도 성공 응답을 반환한다.
- `(mission_log_id, member_id, reaction_type)` 기준 멱등 삭제다. 같은 저장 문자열만 삭제하며 다른 emoji token row는 유지한다.
- `reaction_type` query parameter는 필수다. 클라이언트는 emoji token을 URL encoding해서 전송해야 하며, 서버는 POST와 같은 trim/blank/length 검증을 적용한다.
- 삭제도 `mission_log` 원본, 정산, 환급, 포인트 원장, 상태 생명주기에 영향을 주지 않는다.

# 피드 / 리액션

## `GET /api/feed`

> 내가 참여 중인 크루들의 인증 피드(인증 사진 스트림)를 조회한다. 기본은 내 전체 크루이며, 특정 크루로 필터링할 수 있다.

**Query**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `crew_id` | `integer` | N | 특정 크루로 필터. 생략 시 내가 참여 중인 전체 크루 |
| `from` | `string` | N | 조회 시작일 `YYYY-MM-DD` |
| `to` | `string` | N | 조회 종료일 `YYYY-MM-DD`. `from`만 주면 해당 일자 단건, `from`~`to`면 기간 |
| `limit` | `integer` | N | feed_items 페이지 크기. 기본 20 |
| `cursor` | `string` | N | 페이지네이션 커서 |

**Response** `200 OK`

```json
{
  "available_crews": [
    { "crew_id": 42, "crew_name": "갓생 6시 기상" },
    { "crew_id": 43, "crew_name": "독서 1챕터" }
  ],
  "feed_items": [
    {
      "mission_log_id": 9001,
      "crew_id": 42,
      "crew_name": "갓생 6시 기상",
      "crew_participant_id": 101,
      "member_uuid": "018f4fd2-6d7a-7a41-9f58-6d07f5c3c907",
      "nickname": "돈독러",
      "profile_image_url": "https://cdn.example.com/profile/abc.jpg",
      "image_url": "https://cdn.example.com/mission/9001.jpg",
      "caption": "오늘도 미션 완료했습니다",
      "server_time": "2026-05-11T06:05:10+09:00",
      "certification_status": "SUCCESS",
      "reaction_counts": { "👏": 2, "🔥": 1 },
      "my_reactions": ["👏"],
      "reject_reason_code": null,
      "decision_type": "AUTO_APPROVE"
    }
  ],
  "next_cursor": "2026-05-11T06:05:10+09:00_9001"
}
```

**feed_items 추가 필드**

| 필드 | 타입 | 설명 |
|------|------|------|
| `reject_reason_code` | `string` (nullable) | 거절 사유 코드. `FAILED` 상태의 수동 거절 시에만 존재. 값: `TIME_VIOLATION` / `DUPLICATE` / `MISSION_MISMATCH` / `UNCLEAR` / `INAPPROPRIATE` / `OTHER` |
| `decision_type` | `string` (nullable) | 심사 결정 유형. 심사 완료 전(`PENDING_REVIEW`)이면 `null`. 값: `MANUAL_APPROVE` / `MANUAL_REJECT` / `AUTO_APPROVE` / `AUTO_REJECT` |

**Error**

- `CREW_ACCESS_DENIED`
- `INVALID_CURSOR`

**정책**

- 기본 범위는 **호출자가 참여 중인 전체 크루**의 인증 활동이다. `crew_id`를 주면 해당 크루만 필터링하며, 호출자가 그 크루 참여자가 아니면 `CREW_ACCESS_DENIED`를 반환한다(크루 존재 여부는 밝히지 않는다).
- `available_crews[]`는 필터 칩 구성을 위한 **호출자 참여 크루 목록**(`crew_id`, `crew_name`)이다. "전체 크루" 칩은 클라이언트가 구성한다.
- `feed_items[]`는 실제 `mission_log` row 기반 append-only stream이며 모든 `certification_status`(`SUCCESS`/`PENDING_REVIEW`/`FAILED`)를 노출한다. 상태 필터는 제공하지 않고, `certification_status`는 아이템의 상태 뱃지 표시용이다.
- 정렬/페이지네이션은 `server_time` + `mission_log_id` 기준(최신순, 제출된 순서)이다. `next_cursor`는 `{server_time}_{mission_log_id}` 형식이고 다음 페이지가 없으면 `null`이다.
- 날짜 필터는 **단일 날짜** 또는 **기간** 두 모드다(모두 `YYYY-MM-DD`).
    - 단일 날짜: `from`만(또는 `from`=`to`) → 해당 일자 인증만 조회.
    - 기간: `from`(시작일)~`to`(종료일) → 해당 기간 인증 조회.
- 종료일을 시작일보다 앞서 고르면 FE가 시작일로 교정 후 재선택시키므로, 클라이언트는 **항상 `from <= to`로 호출**한다. 서버는 이를 전제로 `[from, to]`를 조회하고 별도 범위 검증은 하지 않는다. 날짜 형식 오류는 일반 입력 검증으로 처리한다.
- `server_time`은 서버가 인증 요청을 수신한 시각으로 **인증/정산 인정 timing anchor**이자, 피드의 **표시 시각·날짜 필터·정렬/커서** 기준이다. (`created_at`은 인프라 audit 컬럼이며 피드 응답·정렬에는 사용하지 않는다.)
- `crew_id`/`crew_name`은 cross-crew 피드에서 각 아이템의 소속 크루를 표시한다.
- `profile_image_url`은 작성자(member) 프로필 이미지 URL이며 없으면 `null`이다.
- 같은 참여자/같은 날짜/cadence slot에 여러 `mission_log` row(`FAILED`/`PENDING_REVIEW` 재업로드, host moderation 전이)는 모두 visible item으로 유지하며 삭제/overwrite하지 않는다.
- `reaction_counts`는 `mission_log_reaction`에서 파생하며 `mission_log`에 카운터를 저장/갱신하지 않는다. emoji token을 key로 하는 동적 map이다.
- `reaction_counts`/`my_reactions`는 `certification_status`와 무관하게 **모든 feed item**에 대해 채워진다. 리액션은 피드에 노출되는 모든 인증 로그(성공/실패/검토중)에 허용된다.
- `caption`은 피드 표시용이며 단독 인증/정산 기준이 아니다.
- 피드의 성공 표시는 정산 인정 여부/환급액/포인트 잔액을 보장하지 않는다. 최종 정산 포함 여부는 `settlement_item.calculation_reason`이 결정한다.

---

## `GET /api/mission-logs/{missionLogId}`

> 미션 인증 로그 단건 상세를 조회한다. 피드 아이템과 동일한 구조를 반환한다.

**Path**

| 필드 | 타입 | 설명 |
|------|------|------|
| `missionLogId` | `integer` | 조회할 미션 인증 로그 ID |

**Response** `200 OK`

```json
{
  "mission_log_id": 9001,
  "crew_id": 42,
  "crew_name": "갓생 6시 기상",
  "crew_participant_id": 101,
  "member_uuid": "018f4fd2-6d7a-7a41-9f58-6d07f5c3c907",
  "nickname": "돈독러",
  "profile_image_url": "https://cdn.example.com/profile/abc.jpg",
  "image_url": "https://cdn.example.com/mission/9001.jpg",
  "caption": "오늘도 미션 완료했습니다",
  "server_time": "2026-05-11T06:05:10+09:00",
  "certification_status": "SUCCESS",
  "reaction_counts": { "👏": 2, "🔥": 1 },
  "my_reactions": ["👏"],
  "reject_reason_code": null,
  "decision_type": "AUTO_APPROVE"
}
```

응답 필드 구조와 의미는 `GET /api/feed`의 `feed_items[]` 단일 항목과 동일하다.

**Error**

- `MISSION_LOG_NOT_FOUND` — 해당 ID의 인증 로그가 존재하지 않음
- `CREW_ACCESS_DENIED` — 호출자가 해당 인증 로그가 속한 크루의 LOCKED 참여자가 아님

**정책**

- 접근 권한은 **호출자가 해당 크루의 LOCKED 참여자**인 경우에만 허용한다. 미참여 크루의 로그를 요청하면 크루 존재 여부를 밝히지 않고 `CREW_ACCESS_DENIED`를 반환한다.
- 존재하지 않는 `missionLogId`이면 `MISSION_LOG_NOT_FOUND`를 반환한다.
- `image_url`, `profile_image_url`은 `GET /api/feed`와 동일하게 Presigned URL로 반환한다.
- `reaction_counts` / `my_reactions`는 `certification_status`와 무관하게 항상 채워진다.

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

- 리액션 대상은 **호출자가 참여 중인 크루의 인증 로그**다(`certification_status` 무관, 성공/실패/검토중 모두 허용). 호출자가 참여하지 않는 크루의 로그에 리액션하면 `REACTION_NOT_ALLOWED`를 반환한다.
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
- `INVALID_REACTION_TYPE`

**정책**

- 리액션 대상은 호출자가 참여 중인 크루의 인증 로그다(상태 무관). 비참여 크루 로그면 `REACTION_NOT_ALLOWED`를 반환한다.
- 리액션이 이미 없어도 성공 응답을 반환한다.
- `(mission_log_id, member_id, reaction_type)` 기준 멱등 삭제다. 같은 저장 문자열만 삭제하며 다른 emoji token row는 유지한다.
- `reaction_type` query parameter는 필수다. 클라이언트는 emoji token을 URL encoding해서 전송해야 하며, 서버는 POST와 같은 trim/blank/length 검증을 적용한다.
- 삭제도 `mission_log` 원본, 정산, 환급, 포인트 원장, 상태 생명주기에 영향을 주지 않는다.

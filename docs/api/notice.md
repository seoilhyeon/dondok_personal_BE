# 크루 공지 / 댓글 / 리액션

> 채팅 없는 MVP에서 크루 내 소통 수단을 제공한다. 공지 본문은 `crew`, `mission_rule`, `mission_log`, `settlement`, `point_history`의 canonical rule/state를 변경하지 않는다.

## `GET /api/crews/{crewId}/notices`

> 크루의 공지 목록을 조회한다.

**Query**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `cursor` | `string` | N | 이전 응답의 `next_cursor`로 다음 slice를 조회한다. |
| `limit` | `integer` | N | 기본 20, 최대 100. |

**Response** `200 OK`

```json
{
  "items": [
    {
      "notice_id": 1,
      "crew_id": 42,
      "author_member_uuid": "018f4fd2-6d7a-7a41-9f58-6d07f5c3c901",
      "author_nickname": "돈독방장",
      "title": "이번 주 인증 안내",
      "content": "매일 오전 9시 전까지 인증해주세요.",
      "created_at": "2026-05-11T10:00:00+09:00",
      "my_reactions": ["👍"],
      "reaction_counts": { "👍": 3 }
    }
  ],
  "next_cursor": null
}
```

**Error**

- `CREW_NOT_FOUND`
- `CREW_ACCESS_DENIED`

**정책**

- 최신순(`created_at DESC, notice_id DESC`) 정렬.
- `my_reactions`는 요청자 본인이 남긴 리액션 목록, `reaction_counts`는 전체 멤버의 리액션 타입별 집계다.
- `next_cursor`는 다음 slice가 존재할 때만 응답에 포함하며, 없거나 `null`이면 더 조회할 slice가 없다.

---

## `GET /api/crews/{crewId}/notices/{noticeId}`

> 크루 멤버가 공지 상세 내용을 조회한다.

**Response** `200 OK`

```json
{
  "notice_id": 1,
  "crew_id": 42,
  "author_member_uuid": "018f4fd2-6d7a-7a41-9f58-6d07f5c3c901",
  "author_nickname": "돈독방장",
  "title": "이번 주 인증 안내",
  "content": "매일 오전 9시 전까지 인증해주세요.",
  "created_at": "2026-05-11T10:00:00+09:00",
  "my_reactions": ["👍"],
  "reaction_counts": { "👍": 3 }
}
```

**Error**

- `CREW_NOT_FOUND`
- `CREW_ACCESS_DENIED`
- `NOTICE_NOT_FOUND`

**정책**

- `LOCKED` 상태 참여자만 조회할 수 있다.
- `my_reactions`는 요청자 본인이 남긴 리액션 목록이다.
- `reaction_counts`는 전체 멤버의 리액션 타입별 집계다.

---

## `POST /api/crews/{crewId}/notices`

> 방장이 공지를 작성한다.

**Request**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `title` | `string` | Y | 공지 제목 |
| `content` | `string` | Y | 공지 내용 |

**Response** `201 Created`

**Error**

- `CREW_NOT_FOUND`
- `FORBIDDEN_NOT_HOST`

---

## `PATCH /api/crews/{crewId}/notices/{noticeId}`

> 방장이 공지를 수정한다.

**Request**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `title` | `string` | N | 공지 제목 |
| `content` | `string` | N | 공지 내용 |

**Response** `200 OK`

**Error**

- `CREW_NOT_FOUND`
- `NOTICE_NOT_FOUND`
- `FORBIDDEN_NOT_HOST`

---

## `DELETE /api/crews/{crewId}/notices/{noticeId}`

> 공지를 표시 상태(`HIDDEN`/`DELETED`)로 삭제 처리한다.

**Response** `200 OK`

**정책**

- 물리 삭제가 아니라 표시 상태 전이를 사용한다.

---

## `GET /api/crews/{crewId}/notices/{noticeId}/comments`

> 공지의 댓글 목록을 조회한다.

**Query**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `cursor` | `string` | N | 이전 응답의 `next_cursor`로 다음 slice를 조회한다. |
| `limit` | `integer` | N | 기본 20, 최대 100. |

**Response** `200 OK`

```json
{
  "items": [
    {
      "comment_id": 101,
      "notice_id": 1,
      "author_member_uuid": "018f4fd2-6d7a-7a41-9f58-6d07f5c3c907",
      "nickname": "돈독러",
      "author_profile_image_url": null,
      "content": "확인했습니다!",
      "created_at": "2026-05-11T10:30:00+09:00"
    }
  ],
  "next_cursor": null
}
```

**Error**

- `CREW_NOT_FOUND`
- `NOTICE_NOT_FOUND`

**정책**

- 오래된순(`created_at ASC, comment_id ASC`) 정렬.
- `next_cursor`는 다음 slice가 존재할 때만 응답에 포함하며, 없거나 `null`이면 더 조회할 slice가 없다.

---

## `POST /api/crews/{crewId}/notices/{noticeId}/comments`

> 공지에 댓글을 작성한다.

**Request**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `content` | `string` | Y | 댓글 내용 |

**Response** `201 Created`

```json
{
  "comment_id": 101,
  "notice_id": 1,
  "author_member_uuid": "018f4fd2-6d7a-7a41-9f58-6d07f5c3c907",
  "nickname": "돈독러",
  "author_profile_image_url": null,
  "content": "확인했습니다!",
  "created_at": "2026-05-11T10:30:00+09:00"
}
```

**Error**

- `CREW_NOT_FOUND`
- `NOTICE_NOT_FOUND`
- `CREW_ACCESS_DENIED`

---

## `PATCH /api/crews/{crewId}/notices/{noticeId}/comments/{commentId}`

> 본인이 작성한 댓글을 수정한다.

**Request**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `content` | `string` | Y | 수정할 댓글 내용 |

**Response** `200 OK`

**Error**

- `COMMENT_NOT_FOUND`
- `FORBIDDEN`

---

## `DELETE /api/crews/{crewId}/notices/{noticeId}/comments/{commentId}`

> 댓글을 표시 상태(`HIDDEN`/`DELETED`)로 삭제 처리한다.

**Response** `200 OK`

**정책**

- 물리 삭제가 아니라 표시 상태 전이를 사용한다.

---

## `POST /api/crews/{crewId}/notices/{noticeId}/reactions`

> 공지에 이모지 리액션을 멱등 추가한다.

**Request**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `reaction_type` | `string` | Y | emoji token 문자열 |

**Response** `200 OK`

```json
{
  "notice_id": 1,
  "my_reactions": ["👍"],
  "reaction_counts": { "👍": 3 }
}
```

**정책**

- `(notice_id, member_id, reaction_type)` 기준 멱등 upsert다.

---

## `DELETE /api/crews/{crewId}/notices/{noticeId}/reactions/me`

> 공지에 남긴 내 이모지 리액션을 멱등 삭제한다.

**Query**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `reaction_type` | `string` | Y | 삭제할 emoji token (URL encoding 필요) |

**Response** `200 OK`

```json
{
  "notice_id": 1,
  "my_reactions": [],
  "reaction_counts": { "👍": 2 }
}
```

**정책**

- 리액션이 이미 없어도 성공 응답을 반환한다.

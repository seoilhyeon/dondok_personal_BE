# GET /api/me/crews — 내 크루 목록 조회

## 개요

로그인한 회원이 참여 중인 크루(LOCKED 상태) 목록을 커서 페이지네이션으로 조회한다.

---

## Request

```
GET /api/me/crews
Authorization: Bearer {accessToken}
```

### Query Parameters

| 파라미터 | 타입   | 필수 | 기본값 | 설명                                     |
|----------|--------|------|--------|------------------------------------------|
| role     | string | 아니오 | ALL  | HOST·MEMBER·ALL 중 하나. 역할로 필터링. |
| cursor   | string | 아니오 | -    | 커서 값 (이전 응답의 next_cursor)        |
| limit    | int    | 아니오 | 20   | 페이지 크기 (1 이상 100 이하)            |

---

## Response

### 200 OK

```json
{
  "items": [
    {
      "crew_id": 1,
      "title": "아침 달리기 크루",
      "image_url": "https://cdn.example.com/crew/1/image.jpg",
      "category": "EXERCISE",
      "status": "ACTIVE",
      "deposit_amount": 10000,
      "my_role": "HOST",
      "my_status": "LOCKED",
      "start_at": "2026-06-01T00:00:00+09:00",
      "end_at": "2026-06-30T23:59:59+09:00"
    }
  ],
  "next_cursor": "Mg"
}
```

### 응답 필드

| 필드                  | 타입          | 설명                                          |
|-----------------------|---------------|-----------------------------------------------|
| items                 | array         | 크루 항목 배열                                |
| items[].crew_id       | integer       | 크루 ID                                       |
| items[].title         | string        | 크루 이름                                     |
| items[].image_url     | string\|null  | 크루 이미지 URL (없으면 null)                 |
| items[].category      | string        | 크루 카테고리                                 |
| items[].status        | string        | 크루 상태 (RECRUITING·ACTIVE·COMPLETED 등)    |
| items[].deposit_amount| integer       | 내가 납부한 보증금 (원 단위)                  |
| items[].my_role       | string        | 나의 역할 (HOST \| MEMBER)                    |
| items[].my_status     | string        | 나의 참여 상태 (항상 LOCKED)                  |
| items[].start_at      | OffsetDateTime| 크루 시작일 (Asia/Seoul offset)               |
| items[].end_at        | OffsetDateTime| 크루 종료일 (Asia/Seoul offset)               |
| next_cursor           | string\|null  | 다음 페이지 커서. 마지막 페이지이면 null.     |

> **TODO**: 정산 API 완료 후 관련 필드 추가 예정.

---

## 정책

- `status = LOCKED` 참여자만 조회한다. PENDING·CANCELLED 등 다른 상태는 제외.
- `role = HOST`: 내가 방장인 크루만 반환.
- `role = MEMBER`: 내가 일반 멤버(방장 아님)인 크루만 반환.
- `role = ALL` (기본값): 모든 LOCKED 크루 반환.
- 커서는 참여자 ID 기반 오름차순 페이지네이션.

---

## Error

| HTTP | code              | 설명                     |
|------|-------------------|--------------------------|
| 400  | INVALID_CURSOR    | 커서 값 형식이 잘못됨    |
| 401  | UNAUTHORIZED      | 인증 정보 없음           |

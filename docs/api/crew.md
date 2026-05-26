# 크루 / 참여

## `GET /api/crews`

크루 목록을 상태 필터로 조회한다.

**Query**

| 필드     | 타입     | 필수 | 설명                |
| -------- | -------- | ---- | ------------------- |
| `status` | `string` | N    | 기본값 `RECRUITING` |

**Response** `200 OK`

```json
{
  "items": [
    {
      "crew_id": 42,
      "title": "새벽 기상 챌린지",
      "image_url": null,
      "status": "RECRUITING",
      "deposit_amount": 100000,
      "min_participants": 2,
      "max_participants": 5,
      "frequency_type": "DAILY",
      "frequency_count": null,
      "mission_schedule_days": [],
      "recruitment_deadline": "2026-05-09T23:59:59+09:00",
      "start_at": "2026-05-10T00:00:00+09:00",
      "activated_at": null,
      "end_at": "2026-05-31T23:59:59+09:00"
    }
  ]
}
```

---

## `POST /api/crews`

새 크루를 생성한다.

**Request**

| 필드                    | 타입             | 필수 | 설명                                                    |
| ----------------------- | ---------------- | ---- | ------------------------------------------------------- |
| `title`                 | `string`         | Y    | 크루 제목                                               |
| `description`           | `string`         | Y    | 크루 설명                                               |
| `image_s3_key`          | `string \| null` | N    | 사전 업로드된 대표 이미지 S3 key. 표시용 metadata       |
| `category`              | `string`         | Y    | 카테고리                                                |
| `deposit_amount`        | `integer`        | Y    | 보증금 (1,000원 단위, 1,000 ~ 100,000원)                |
| `min_participants`      | `integer`        | N    | 최소 인원. 기본값 `2`                                   |
| `max_participants`      | `integer`        | Y    | 최대 인원 (최대 15)                                     |
| `frequency_type`        | `string`         | Y    | `DAILY` 또는 `SPECIFIC_DAYS`                            |
| `mission_schedule_days` | `string[]`       | N    | `SPECIFIC_DAYS`일 때 필수. 예: `["MONDAY","WEDNESDAY"]` |
| `daily_settlement_type` | `string`         | Y    | `A`, `B`, `C` 중 하나                                   |
| `host_agreement`        | `object`         | Y    | 방장 약관 동의 스냅샷 payload                           |
| `recruitment_deadline`  | `string`         | Y    | ISO-8601. 신규 참여 마감 시각                           |
| `start_date`            | `string`         | Y    | `YYYY-MM-DD`. 시작일                                    |
| `end_date`              | `string`         | Y    | `YYYY-MM-DD`. 종료일                                    |

**Response** `201 Created`

```json
{
  "crew_id": 42,
  "title": "새벽 기상 챌린지",
  "description": "매일 새벽 6시 전 기상 인증",
  "image_url": null,
  "category": "EXERCISE",
  "status": "RECRUITING",
  "deposit_amount": 100000,
  "min_participants": 2,
  "max_participants": 5,
  "frequency_type": "SPECIFIC_DAYS",
  "frequency_count": null,
  "mission_schedule_days": ["MONDAY", "WEDNESDAY", "FRIDAY"],
  "daily_settlement_type": "A",
  "host_agreement_version": "v1",
  "host_agreed_at": "2026-05-07T09:00:00+09:00",
  "recruitment_deadline": "2026-05-09T23:59:59+09:00",
  "start_at": "2026-05-10T00:00:00+09:00",
  "activated_at": null,
  "end_at": "2026-05-31T23:59:59+09:00",
  "created_at": "2026-05-07T09:00:00+09:00"
}
```

**Error**

- `VALIDATION_ERROR`
- `INVALID_DEPOSIT_AMOUNT`
- `INVALID_FREQUENCY_RULE`
- `INVALID_CATEGORY`
- `INVALID_DAILY_SETTLEMENT_TYPE`
- `HOST_AGREEMENT_REQUIRED`

**정책**

- `2 <= min_participants <= max_participants <= 15`
- `start_date`, `end_date`는 서버에서 `Asia/Seoul` 기준 `start_at`, `end_at`으로 변환한다.
- `RECRUITING → ACTIVE` 전환은 `start_at`에 시스템이 자동으로 수행한다. host/admin manual 전환은 없다.

---

## `GET /api/crews/{crewId}`

특정 크루의 상세 정보와 내 참여 현황을 조회한다.

**Response** `200 OK`

```json
{
  "crew_id": 42,
  "host_member_uuid": "018f4fd2-6d7a-7a41-9f58-6d07f5c3c901",
  "title": "새벽 기상 챌린지",
  "description": "매일 아침 6시 전에 인증",
  "image_url": null,
  "category": "EXERCISE",
  "status": "ACTIVE",
  "settlement_status": "NONE",
  "deposit_amount": 100000,
  "min_participants": 2,
  "max_participants": 5,
  "frequency_type": "DAILY",
  "frequency_count": null,
  "mission_schedule_days": [],
  "daily_settlement_type": "A",
  "host_agreement_version": "v1",
  "host_agreed_at": "2026-05-07T09:00:00+09:00",
  "recruitment_deadline": "2026-05-09T23:59:59+09:00",
  "start_at": "2026-05-10T00:00:00+09:00",
  "activated_at": "2026-05-10T00:00:00+09:00",
  "end_at": "2026-05-31T23:59:59+09:00",
  "my_participation": {
    "crew_participant_id": 101,
    "status": "LOCKED",
    "deposit_locked_amount": 100000,
    "locked_at": "2026-05-08T13:00:00+09:00",
    "withdrawn_at": null
  }
}
```

**Error**

- `CREW_NOT_FOUND`

**정책**

- `my_participation`은 참여 이력이 없으면 `null`이다.
- `settlement_status`는 조회 편의용 projection이며, 정산 상태의 원천은 `Settlement.status`다.

---

## `POST /api/crews/{crewId}/participants`

크루에 참여를 신청하고 보증금을 예약한다.

**Request** body 없음

**Response** `201 Created`

```json
{
  "crew_participant_id": 101,
  "crew_id": 42,
  "member_uuid": "018f4fd2-6d7a-7a41-9f58-6d07f5c3c907",
  "status": "PENDING",
  "deposit_reserved_amount": 100000,
  "deposit_locked_amount": 0,
  "locked_at": null,
  "pending_at": "2026-05-08T13:00:00+09:00"
}
```

**Error**

- `CREW_NOT_FOUND`
- `CREW_NOT_RECRUITING`
- `CAPACITY_FULL`
- `INSUFFICIENT_BALANCE`
- `ALREADY_PARTICIPATING`
- `APPLICATION_NOT_ALLOWED`

**정책**

- `RECRUITING` 상태이고 `recruitment_deadline` 이전일 때만 신청 가능하다.
- 신청 시 `deposit_amount`만큼 잔액을 reserve한다.
- `PENDING` 상태는 capacity에 포함하나 정산 대상은 아니다.
- terminal 상태(`REJECTED`, `CANCELLED`, `EXPIRED`)에서 재신청은 불가하다.

---

## `DELETE /api/crews/{crewId}/participants/me`

크루 참여 신청을 취소한다.

**Request** body 없음

**Response** `200 OK`

```json
{
  "crew_participant_id": 101,
  "crew_id": 42,
  "status": "CANCELLED",
  "cancelled_at": "2026-05-08T14:00:00+09:00"
}
```

**Error**

- `CREW_NOT_FOUND`
- `PARTICIPANT_NOT_FOUND`
- `APPLICATION_NOT_CANCELLABLE`

**정책**

- `PENDING` 상태에서만 취소 가능하다.
- 취소 시 reserved 보증금은 `CREW_CANCELLED_REFUND`로 즉시 반환한다.

---

## `POST /api/crews/{crewId}/applications/{crewParticipantId}/approve`

방장이 크루 참여 신청을 승인한다.

**Request** body 없음

**Response** `200 OK`

```json
{
  "crew_participant_id": 101,
  "crew_id": 42,
  "status": "LOCKED",
  "deposit_locked_amount": 100000,
  "locked_at": "2026-05-08T15:00:00+09:00"
}
```

**Error**

- `CREW_NOT_FOUND`
- `PARTICIPANT_NOT_FOUND`
- `FORBIDDEN_NOT_HOST`
- `APPLICATION_NOT_APPROVABLE`

**정책**

- 호출자가 해당 크루의 host여야 한다.
- `PENDING` 상태에서만 승인 가능하다.
- 승인 시 기존 reserve를 `LOCKED`로 확정한다. 추가 잔액 차감은 없다.

---

## `POST /api/crews/{crewId}/applications/{crewParticipantId}/reject`

방장이 크루 참여 신청을 거절한다.

**Request** body 없음

**Response** `200 OK`

```json
{
  "crew_participant_id": 101,
  "crew_id": 42,
  "status": "REJECTED",
  "rejected_at": "2026-05-08T15:00:00+09:00"
}
```

**Error**

- `CREW_NOT_FOUND`
- `PARTICIPANT_NOT_FOUND`
- `FORBIDDEN_NOT_HOST`
- `APPLICATION_NOT_REJECTABLE`

**정책**

- 호출자가 해당 크루의 host여야 한다.
- `PENDING` 상태에서만 거절 가능하다.
- 거절 시 reserved 보증금은 `CREW_CANCELLED_REFUND`로 즉시 반환한다.

---

## `GET /api/crews/{crewId}/applications`

방장이 크루 가입 신청 목록을 조회한다.

**Query**

| 필드     | 타입      | 필수 | 설명                                                                       |
| -------- | --------- | ---- | -------------------------------------------------------------------------- |
| `status` | `string`  | N    | `PENDING`, `LOCKED`, `REJECTED`, `CANCELLED`, `EXPIRED`. 생략 시 `PENDING` |
| `cursor` | `string`  | N    | 이전 응답의 `next_cursor`로 다음 slice를 조회한다                          |
| `limit`  | `integer` | N    | 기본 50, 최대 200                                                          |

**Response** `200 OK`

```json
{
  "items": [
    {
      "crew_participant_id": 101,
      "member_uuid": "018f4fd2-6d7a-7a41-9f58-6d07f5c3c907",
      "nickname": "돈독러",
      "profile_image_url": null,
      "status": "PENDING",
      "applied_at": "2026-05-08T13:00:00+09:00",
      "decided_at": null
    }
  ],
  "next_cursor": null
}
```

**Error**

- `CREW_NOT_FOUND`
- `FORBIDDEN_NOT_HOST`

**정책**

- 호출자가 해당 크루의 host여야 한다.
- 기본 정렬은 `applied_at DESC`이며, `status` 필터를 생략하면 `PENDING` 목록만 반환한다.
- 이 API는 신청 처리를 위한 읽기 전용 조회 surface다. 승인/거절/취소는 별도 endpoint를 사용한다.

---

## `GET /api/crews/{crewId}/members`

크루 멤버 목록을 조회한다.

**Query**

| 필드     | 타입      | 필수 | 설명                                                              |
| -------- | --------- | ---- | ----------------------------------------------------------------- |
| `state`  | `string`  | N    | `ACTIVE`(진행 중인 멤버: `LOCKED` 등), `LOCKED`. 생략 시 `ACTIVE` |
| `cursor` | `string`  | N    | 이전 응답의 `next_cursor`로 다음 slice를 조회한다                 |
| `limit`  | `integer` | N    | 기본 50, 최대 200                                                 |

**Response** `200 OK`

```json
{
  "items": [
    {
      "crew_participant_id": 101,
      "member_uuid": "018f4fd2-6d7a-7a41-9f58-6d07f5c3c907",
      "nickname": "돈독러",
      "profile_image_url": null,
      "role": "HOST",
      "status": "LOCKED",
      "joined_at": "2026-05-08T13:00:00+09:00"
    }
  ],
  "next_cursor": null
}
```

**Error**

- `CREW_NOT_FOUND`
- `CREW_ACCESS_DENIED`

**정책**

- 해당 크루의 참여자 또는 호스트여야 한다.
- `role`은 `HOST` 또는 `MEMBER`이며, `crew.host_member_id` 매칭에서 파생한 projection이다. 권한 부여 컬럼이 아니다.
- `joined_at`은 `crew_participant.locked_at`에 해당하는 projection이다.
- 정산 결과(인정 횟수, 환급액 등) 필드는 포함하지 않는다. 정산 결과는 `GET /api/settlements/{settlementId}`로 조회한다.

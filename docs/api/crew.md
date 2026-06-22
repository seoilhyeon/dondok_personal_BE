# 크루 / 참여

## `GET /api/crews`

> 크루 목록을 상태 필터 및 검색 조건으로 조회한다.

**Query**

| 필드       | 타입      | 필수 | 설명                                                 |
| ---------- | --------- | ---- | -------------------------------------------------- |
| `status`   | `string`  | N    | 기본값 `RECRUITING`                                |
| `category` | `string`  | N    | 카테고리 필터 (예: `MORNING`, `READING`, `EXERCISE`) |
| `keyword`  | `string`  | N    | 크루 제목(`title`) 부분 일치 검색어                |
| `cursor`   | `string`  | N    | 이전 응답의 `next_cursor`로 다음 slice를 조회한다. |
| `limit`    | `integer` | N    | 기본 20, 최대 100. |

**Response** `200 OK`

```json
{
  "items": [
    {
      "crew_id": 42,
      "title": "새벽 기상 챌린지",
      "image_url": null,
      "category": "MORNING",
      "status": "RECRUITING",
      "deposit_amount": 100000,
      "min_participants": 2,
      "max_participants": 5,
      "current_participants": 1,
      "frequency_type": "DAILY",
      "mission_schedule_days": [],
      "recruitment_deadline": "2026-05-09T23:59:59+09:00",
      "start_at": "2026-05-10T00:00:00+09:00",
      "activated_at": null,
      "end_at": "2026-05-31T23:59:59+09:00"
    }
  ],
  "next_cursor": null
}
```

**정책**

- `next_cursor`는 다음 slice가 존재할 때만 응답에 포함하며, 없거나 `null`이면 더 조회할 slice가 없다.

---

## `POST /api/crews`

> 새 크루를 생성한다.

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
| `daily_settlement_type` | `string`         | Y    | `A` (인증마감 09:00 / 정산 12:00), `B` (인증마감 21:00 / 정산 00:00), `C` (인증마감 23:59 / 정산 익일 12:00) |
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
  "mission_schedule_days": ["MONDAY", "WEDNESDAY", "FRIDAY"],
  "daily_settlement_type": "A",
  "host_agreement_version": "v1",
  "host_agreed_at": "2026-05-07T09:00:00+09:00",
  "recruitment_deadline": "2026-05-09T23:59:59+09:00",
  "start_at": "2026-05-10T00:00:00+09:00",
  "activated_at": null,
  "end_at": "2026-05-31T23:59:59+09:00",
  "created_at": "2026-05-07T09:00:00+09:00",
  "my_participation": {
    "crew_participant_id": 100,
    "status": "LOCKED",
    "deposit_locked_amount": 100000,
    "locked_at": "2026-05-07T09:00:00+09:00"
  }
}
```

**Error**

- `INVALID_INPUT`
- `INVALID_DEPOSIT_AMOUNT`
- `INVALID_FREQUENCY_RULE`
- `INVALID_CATEGORY`
- `INVALID_DAILY_SETTLEMENT_TYPE`
- `HOST_AGREEMENT_REQUIRED`
- `HOST_CREW_LIMIT_EXCEEDED` — 방장으로 운영 중인 크루(RECRUITING + ACTIVE)가 이미 5개인 경우
- `INSUFFICIENT_BALANCE`

**정책**

- `2 <= min_participants <= max_participants <= 15`
- `start_date`, `end_date`는 서버에서 `Asia/Seoul` 기준 `start_at`, `end_at`으로 변환한다.
- `RECRUITING → ACTIVE` 전환은 `start_at`에 시스템이 자동으로 수행한다. host/admin manual 전환은 없다.
- 크루 생성 트랜잭션은 `crew` row insert와 함께 호스트용 `crew_participant` row를 `status=LOCKED`로 자동 생성하고, `point_account.available_balance -= crew.deposit_amount` / `locked_balance += crew.deposit_amount` bucket update와 `CREW_DEPOSIT_RESERVE point_history` row insert를 함께 처리한다. 호스트는 처음부터 `LOCKED`이므로 `PENDING`/`reserved_balance` bucket을 거치지 않고, 별도 `POST /api/crews/{crewId}/participants` 신청 + 방장 승인 흐름도 거치지 않는다.
- 호스트 잔액이 `crew.deposit_amount` 미만이면 lock 처리가 실패하므로 크루 생성 자체를 `INSUFFICIENT_BALANCE`로 거절한다. 호스트에게 별도 보증금 면제/예외는 없다.
- 호스트 auto-created `LOCKED` participant는 일반 `LOCKED` 참여자와 동일하게 capacity, `min_participants` baseline, activation eligibility, frozen participant baseline, settlement eligibility에 포함되며 최종 정산 대상이다.
- 호스트의 `CREW_DEPOSIT_RESERVE` 원장은 일반 신청 reserve와 동일한 `transaction_type`을 사용하지만 bucket destination은 `locked_balance`다. 별도 `HOST_*` enum/type을 만들지 않는다.
- 응답의 `my_participation`은 호스트 본인의 auto-created `LOCKED` participant snapshot이다.

---

## `GET /api/crews/{crewId}`

> 특정 크루의 상세 정보와 내 참여 현황을 조회한다.

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
    "locked_at": "2026-05-08T13:00:00+09:00"
  }
}
```

**Error**

- `CREW_NOT_FOUND`

**정책**

- `my_participation`은 참여 이력이 없으면 `null`이다.
- `settlement_status`는 조회 편의용 projection이며, 정산 상태의 원천은 `Settlement.status`다.

---

## `DELETE /api/crews/{crewId}`

> 방장이 크루를 해체한다. `RECRUITING` 상태인 크루만 해체할 수 있다.

**Request** body 없음

**Response** `200 OK`

```json
{
  "crew_id": 42,
  "status": "CANCELLED",
  "cancelled_at": "2026-05-08T15:00:00+09:00"
}
```

**Error**

- `CREW_NOT_FOUND`
- `FORBIDDEN_NOT_HOST`
- `CREW_DISBAND_NOT_ALLOWED`

**정책**

- 호출자가 해당 크루의 host여야 한다.
- `RECRUITING` 상태인 크루만 해체 가능하다. `ACTIVE`, `CANCELLED`, `COMPLETED` 상태이면 `CREW_DISBAND_NOT_ALLOWED`로 거절한다.
- 해체 시 `PENDING` 참여자의 reserved 보증금은 `CREW_RESERVE_RELEASE` point_history로 즉시 반환한다 (`reserved_balance → available_balance`).
- 해체 시 `LOCKED` 참여자의 보증금은 `CREW_CANCEL_REFUND` point_history로 전액 환급한다 (`locked_balance → available_balance`). 환급 후 FCM 해체 알림(`CREW_DISBANDED`)을 발송한다.
- 크루 상태는 `CANCELLED`로 전환되며 `cancelled_at`이 기록된다.

---

## `POST /api/crews/{crewId}/participants`

> 크루에 참여를 신청하고 보증금을 예약한다.

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
- `CANCELLED` 상태(자진 취소)에서는 재신청이 허용된다. 재신청 조건은 일반 신청과 동일하다: `crew.status = RECRUITING` + 서버 시간이 `recruitment_deadline` 전 + capacity 가능(`PENDING + LOCKED < max_participants`) + reserve 가능(`available_balance >= crew.deposit_amount`). 재신청 시 새 row를 생성하지 않고 기존 `crew_participant` row를 `CANCELLED → PENDING`으로 reopen한다(row resurrection / in-place reopen semantics). `unique(crew_id, member_id)` 제약은 그대로 유지되며 soft delete나 제약 완화 없이 기존 row를 그대로 재사용한다. reopen 시 `released_point_history_id`를 `null`로 reset하고 `pending_at`을 현재 시각으로 갱신한다. 보증금 reserve는 `point_history` append-only 방식으로 새 cycle을 추가하며, idempotency key는 `crew:{crewId}:participant:{participantId}:reserve:{cycle}` 형식으로 cycle별 구분한다. 새 reserve cycle은 해당 participant의 누적 `CREW_RESERVE_RELEASE` 원장 수 + 1로 계산해 duplicate reserve retry가 cycle을 증가시키지 않도록 한다.
- `REJECTED`, `EXPIRED` 상태에서 재신청은 `APPLICATION_NOT_ALLOWED`로 거절한다. MVP에서는 이 두 상태에서 재참여/row 재사용/status 되돌리기를 허용하지 않는다.
- 호스트는 자신이 생성한 크루에 대해 이 endpoint로 다시 신청하지 않는다. `POST /api/crews` 시점에 host용 `crew_participant` row가 이미 `LOCKED`로 auto-created되어 있으므로 호스트의 추가 신청 시도는 `ALREADY_PARTICIPATING`로 거절된다.

---

## `DELETE /api/crews/{crewId}/participants/me`

> 크루 참여 신청을 취소한다.

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
- 취소 시 reserved 보증금은 `CREW_RESERVE_RELEASE` point_history로 반환하고, `point_account.available_balance`를 같은 금액만큼 복구한다. 생성/재사용된 release 원장은 같은 트랜잭션에서 `crew_participant.released_point_history_id`로 연결한다. 이미 연결된 release 원장이 있으면 중복 반환하지 않고 기존 원장을 재사용한다.
- 취소(`CANCELLED`) 이후 동일 크루에 재신청할 수 있다. 재신청은 `POST /api/crews/{crewId}/participants`를 다시 호출하며, 기존 `crew_participant` row를 `CANCELLED → PENDING`으로 reopen한다. 새 row는 생성되지 않으며 `unique(crew_id, member_id)` 제약은 유지된다. `cancelled_at`은 reopen 시 갱신되는 `pending_at`과 별개로 audit 용도로 row에 남는다.

---

## `POST /api/crews/{crewId}/applications/{crewParticipantId}/approve`

> 방장이 크루 참여 신청을 승인한다.

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
- `PENDING` 상태에서만 승인 가능하다. 다른 상태는 `APPLICATION_NOT_APPROVABLE`로 거절한다.
- 승인 시 기존 reserve를 `LOCKED`로 확정한다. 추가 잔액 차감은 없으며 `CREW_DEPOSIT_LOCK` `point_history`를 생성/재사용해 보존한다(`reserved_balance → locked_balance` bucket transition 수행).
- 이 endpoint는 일반 참여자의 `PENDING` row에만 사용한다. `POST /api/crews` 시점에 auto-created된 호스트 본인의 `LOCKED` row는 승인 대상이 아니며, 호출 시 `APPLICATION_NOT_APPROVABLE`로 거절한다.

---

## `POST /api/crews/{crewId}/applications/{crewParticipantId}/reject`

> 방장이 크루 참여 신청을 거절한다.

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
- `PENDING` 상태에서만 거절 가능하다. 다른 상태는 `APPLICATION_NOT_REJECTABLE`로 거절한다.
- 거절 시 reserved 보증금은 `CREW_RESERVE_RELEASE` point_history로 반환하고, `point_account.available_balance`를 같은 금액만큼 복구한다. 생성/재사용된 release 원장은 같은 트랜잭션에서 `crew_participant.released_point_history_id`로 연결한다. 이미 연결된 release 원장이 있으면 중복 반환하지 않고 기존 원장을 재사용한다.
- 이 endpoint는 일반 참여자의 `PENDING` row에만 사용한다. `POST /api/crews` 시점에 auto-created된 호스트 본인의 `LOCKED` row는 거절 대상이 아니며, 호출 시 `APPLICATION_NOT_REJECTABLE`로 거절한다.

---

## `GET /api/crews/{crewId}/applications`

> 방장이 크루 가입 신청 목록을 조회한다.

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

## `GET /api/crews/{crewId}/applications/count`

> 방장이 크루 가입 신청 상태별 건수를 조회한다.

**Response** `200 OK`

```json
{
  "pending_count": 3,
  "locked_count": 12,
  "rejected_count": 1
}
```

**Error**

- `CREW_NOT_FOUND`
- `FORBIDDEN_NOT_HOST`

**정책**

- 호출자가 해당 크루의 host여야 한다.
- `pending_count`는 `PENDING`, `locked_count`는 `LOCKED`, `rejected_count`는 `REJECTED` 상태 참여자 수다.
- 가입 신청 관리 화면의 badge/count 표시용 읽기 전용 API다.

---

## `GET /api/crews/{crewId}/members`

> 크루 멤버 목록을 조회한다.

**Query**

| 필드     | 타입      | 필수 | 설명                                                              |
| -------- | --------- | ---- | ----------------------------------------------------------------- |
| `state`  | `string`  | N    | `ACTIVE` / `LOCKED`. 생략 시 `ACTIVE`. `ACTIVE`는 ParticipantStatus enum 값이 아니라 "active membership" alias이며 MVP에서는 `LOCKED` participant 집합을 의미한다. `LOCKED`는 ParticipantStatus enum 값을 직접 지정한다. |
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

---

## 보증금 Lifecycle 요약

크루 참여에서 정산까지 보증금 흐름의 핵심 규칙을 요약한다.

| 단계 | 처리 |
| --- | --- |
| 크루 생성 (`POST /api/crews`) | host auto-created `LOCKED` participant 생성 + `CREW_DEPOSIT_RESERVE` point_history insert를 같은 트랜잭션에서 처리 |
| 일반 참여 신청 (`POST participants`) | `PENDING` row 생성과 함께 `deposit_amount` reserve (`available_balance → reserved_balance`) |
| 승인 (`approve`) | 추가 잔액 차감 없이 기존 reserve를 `LOCKED`로 확정 (`reserved_balance → locked_balance` bucket transition 수행, `CREW_DEPOSIT_LOCK` row 생성/재사용) |
| 취소 / 거절 / 만료 | `CREW_RESERVE_RELEASE` point_history로 잔액 복구 (`reserved_balance → available_balance`)를 같은 트랜잭션에서 처리 |
| `CANCELLED → PENDING` reopen | 기존 release row를 append-only로 유지한 채 새 reserve cycle 추가. idempotency key는 `crew:{crewId}:participant:{participantId}:reserve:{cycle}` 형식으로 cycle별 구분 |

**재신청 가능 여부**

| 현재 상태 | 재신청 결과 |
| --- | --- |
| `PENDING` / `LOCKED` | `ALREADY_PARTICIPATING` |
| `CANCELLED` | reopen 가능 (`CANCELLED → PENDING`) |
| `REJECTED` / `EXPIRED` | `APPLICATION_NOT_ALLOWED` (terminal, MVP에서 재참여 불가) |
| host auto-created `LOCKED` row | reopen 대상 아님 |

**시스템 Lifecycle 전이**

- `RECRUITING → ACTIVE`: `start_at` 기준 시스템이 자동 전환. host/admin manual 전환 없음.

---

## 공지 / 댓글 / 리액션

> 크루 내 방장 공지, 댓글, 공지 리액션 communication surface를 제공한다. 상세 명세는 `notice.md` 참조.

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/api/crews/{crewId}/notices` | 공지 목록 조회 |
| `POST` | `/api/crews/{crewId}/notices` | 방장 공지 작성 |
| `PATCH` | `/api/crews/{crewId}/notices/{noticeId}` | 방장 공지 수정 |
| `DELETE` | `/api/crews/{crewId}/notices/{noticeId}` | 공지 표시 상태 삭제 |
| `GET` | `/api/crews/{crewId}/notices/{noticeId}/comments` | 공지 댓글 목록 |
| `POST` | `/api/crews/{crewId}/notices/{noticeId}/comments` | 공지 댓글 작성 |
| `PATCH` | `/api/crews/{crewId}/notices/{noticeId}/comments/{commentId}` | 공지 댓글 수정 |
| `DELETE` | `/api/crews/{crewId}/notices/{noticeId}/comments/{commentId}` | 댓글 표시 상태 삭제 |
| `POST` | `/api/crews/{crewId}/notices/{noticeId}/reactions` | 공지 리액션 멱등 upsert |
| `DELETE` | `/api/crews/{crewId}/notices/{noticeId}/reactions/me` | 내 공지 리액션 멱등 삭제 |

**정책**

- 공지 작성/수정 권한은 host 중심으로 검증한다.
- 공지 본문은 `crew`, `mission_rule`, `mission_log`, `settlement`, `point_history`의 canonical rule/state를 변경하지 않는다.
- 댓글과 공지 리액션은 social interaction only이며, 정산 인정 횟수, 환급액, 포인트 원장, 인증 성공/실패, lifecycle 전이에 side effect를 만들지 않는다.
- `reaction_type`은 FE-selected emoji/token string이며 고정 enum으로 freeze하지 않는다.
- 삭제 계열은 물리 삭제가 아니라 표시 상태 전이(`HIDDEN`/`DELETED`)를 우선한다.

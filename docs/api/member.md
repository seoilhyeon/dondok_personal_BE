# 회원

## `POST /api/member/signup`

> 이메일, 비밀번호, 닉네임으로 새 계정을 생성한다.

**Request**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `email` | `string` | Y | 로그인 식별자 |
| `password` | `string` | Y | 비밀번호 원문 |
| `nickname` | `string` | Y | 노출 이름 |

**Response** `201 Created`

```json
{
  "member_uuid": "018f4fd2-6d7a-7a41-9f58-6d07f5c3c901",
  "email": "user@example.com",
  "nickname": "돈독러",
  "status": "ACTIVE",
  "created_at": "2026-05-07T09:00:00+09:00"
}
```

**Error**

- `EMAIL_ALREADY_EXISTS`
- `NICKNAME_ALREADY_EXISTS`
- `INVALID_INPUT`

**정책**

- `email`과 `nickname`은 unique다.
- 가입 직후 자동 로그인 여부는 본 명세에서 고정하지 않는다. MVP 기본 흐름은 가입 후 로그인이다.

---

## `GET /api/me`

> 현재 로그인한 사용자의 프로필 정보를 조회한다.

**Response** `200 OK`

```json
{
  "member_uuid": "018f4fd2-6d7a-7a41-9f58-6d07f5c3c901",
  "email": "user@example.com",
  "nickname": "돈독러",
  "profile_image_url": "https://cdn.example.com/profile/018f4fd2-6d7a-7a41-9f58-6d07f5c3c901/avatar.jpg",
  "status_message": "오늘도 한 걸음 더",
  "is_host_ever": true,
  "hosted_crew_count": 2,
  "status": "ACTIVE",
  "created_at": "2026-05-01T12:00:00+09:00"
}
```

**정책**

- 수정 가능 필드: `nickname`, `profile_image_s3_key`, `status_message`
- `profile_image_s3_key`는 presigned upload로 먼저 업로드된 프로필 이미지 S3 key이며, `null`이면 이미지를 제거한다.
- `profile_image_url`은 저장된 `member.profile_image_s3_key`에서 파생한 읽기 전용 접근 URL이며, 이미지가 없으면 null일 수 있다.
- `status_message`는 자유 입력 한 줄 상태 메시지다(최대 100자).
- `is_host_ever`, `hosted_crew_count`는 read-time 계산 projection이다.

---

## `GET /api/me/activity-summary`

> 프로필 페이지의 `내 활동` 메뉴와 `활동 통계` 카드에 필요한 현재 로그인 사용자의 활동 요약을 조회한다.

**Response** `200 OK`

```json
{
  "member_uuid": "018f4fd2-6d7a-7a41-9f58-6d07f5c3c901",
  "activity_info": {
    "crew": {
      "total_crew_count": 17,
      "active_crew_count": 3,
      "completed_crew_count": 14
    },
    "total_verification_count": 24,
    "unread_notification_count": 2
  },
  "activity_stats": {
    "total_recognized_success_count": 450,
    "highest_share_ratio": "0.250000",
    "highest_share_ratio_crew_id": 42,
    "highest_share_ratio_crew_title": "아침 갓생 30일",
    "average_success_rate": null
  },
  "generated_at": "2026-06-01T09:00:00+09:00"
}
```

**필드 설명**

| 필드                                                          | 타입                | 설명                                                                                                           |
|-------------------------------------------------------------|-------------------|--------------------------------------------------------------------------------------------------------------|
| `member_uuid`                                               | `uuid`            | 현재 로그인 사용자 UUID                                                                                              |
| `activity_info.crew.total_crew_count`                       | `integer`         | 진행/종료 활동 크루의 distinct union. 현재 `CrewStatus` 버킷이 disjoint이므로 `active_crew_count + completed_crew_count`와 같다. |
| `activity_info.crew.active_crew_count`                      | `integer`         | `crew.status IN (RECRUITING, ACTIVE)` 이고 현재 사용자 participant 상태가 `LOCKED`인 크루 수                  |
| `activity_info.crew.completed_crew_count`                   | `integer`         | `crew.status = CLOSED` 이고 현재 사용자 participant 상태가 `LOCKED`인 크루 수                                              |
| `activity_info.total_verification_count`                      | `integer`         | 프로필 검증 내역 메뉴용 현재 사용자 제출 mission log 총개수. 성공/실패 breakdown은 검증 이력 API에서 조회한다.                         |
| `activity_info.unread_notification_count`                    | `integer`         | 프로필 알림 메뉴 배지용 현재 사용자 notification 중 `read_at IS NULL` 개수                                               |
| `activity_stats.total_recognized_success_count`             | `integer`         | `Settlement.status = SUCCEEDED`인 settlement item의 `recognized_success_count` 합                               |
| `activity_stats.highest_share_ratio`                        | `string \| null`  | 성공한 정산 item 중 최대 최종 `share_ratio`. scale 6 decimal string                                                    |
| `activity_stats.highest_share_ratio_crew_id`                | `integer \| null` | 최고 지분율을 만든 크루 ID. 기존 크루/정산 API에서 public identifier로 쓰는 crew ID만 노출한다.                                        |
| `activity_stats.highest_share_ratio_crew_title`             | `string \| null`  | 최고 지분율을 만든 크루 제목                                                                                             |
| `activity_stats.average_success_rate`                       | `string \| null`  | 정산 완료 mission-day 가중 성공률. 안전하게 계산할 수 없으면 null                                                                |
| `generated_at`                                              | `datetime`        | 응답 생성 시각. `Asia/Seoul` offset 포함                                                                             |

**정책**

- 이 API는 프로필 페이지용 read-model이다. 정산, 포인트 원장, 검수, 알림 상태를 변경하지 않는다.
- `GET /api/me`는 사용자 신원/프로필 중심으로 유지하고, 크루/미션/정산/알림 기반 활동 집계는 이 API에서 제공한다.
- 응답은 machine-readable 값만 제공한다. 카드 label, caption, tone, 라우팅, localization은 프론트엔드가 담당한다.
- `activity_info.total_verification_count`는 프로필 검증 내역 메뉴용 인증 활동 요약이다. 검수 대기/성공/실패 breakdown은 검증 이력 API에서 조회하며, `mission_log.SUCCESS`를 최종 정산 인정 성공이나 환급 권한으로 표시하면 안 된다.
- `activity_stats.*`는 `Settlement.status = SUCCEEDED` + `SettlementItem`만 사용한다. raw
  `mission_log.SUCCESS`나 dashboard projection을 사용하지 않는다.
- `settled_crew_count`는 응답에 포함하지 않는다. 종료 크루 수는 `activity_info.crew.completed_crew_count`로 충분하며, 정산 완료 크루 수는 정산/내역 화면에서 별도 맥락으로 다룬다.
- `activity_stats.average_success_rate`는 settlement-time cadence context(
  `settlement.rule_context_snapshot` + settlement item period)로 계산한다. 해당 context가 안전하게 해석되지 않으면
  `null`을 반환하고, 현재 `mission_rule`/`mission_schedule_day`로 과거 정산을 재해석하지 않는다.
- 방장 여부와 운영 메뉴 진입 판단은 `GET /api/me`의 `is_host_ever`, `hosted_crew_count`를 사용한다. 운영 콘솔 배지용 대기 건수는 `GET /api/me/host-operation-summary`에서 조회하며, breakdown, 목록, 승인/거절 액션 데이터는 별도 방장 운영탭 API에서 조회한다.
- `activity_info.unread_notification_count`는 알림 목록의 unread source of truth와 같은 `read_at IS NULL` 기준이다. 알림 상태는 UX 재진입 힌트이며 크루/인증/정산/포인트 원장의 source of truth가 아니다.
- 내부 `member.id`, participant 내부 ID, 내부 FK, `reject_memo`, raw moderation chain, settlement item 내부
  ID는 노출하지 않는다.

**Error**

- `UNAUTHORIZED`
- `MEMBER_NOT_FOUND`

---

## `GET /api/me/host-operation-summary`

> 프로필 탭의 `운영 콘솔` 배지에 표시할 현재 로그인 사용자의 방장 운영 대기 건수를 조회한다.

**Response** `200 OK`

```json
{
  "member_uuid": "018f4fd2-6d7a-7a41-9f58-6d07f5c3c901",
  "total_pending_count": 6,
  "generated_at": "2026-06-01T09:00:00+09:00"
}
```

**필드 설명**

| 필드                    | 타입       | 설명                                                                 |
|-----------------------|----------|--------------------------------------------------------------------|
| `member_uuid`         | `uuid`   | 현재 로그인 사용자 UUID                                                   |
| `total_pending_count` | `integer` | 프로필 운영 콘솔 배지용 대기 건수. 현재 사용자가 host인 크루의 검수 대기 인증과 가입 승인 대기의 합 |
| `generated_at`        | `datetime` | 응답 생성 시각. `Asia/Seoul` offset 포함                                  |

**정책**

- 이 API는 프로필 탭 `운영 콘솔` 메뉴 배지용 read-model이다. 검수, 가입 승인/거절, 알림, 정산, 포인트 상태를 변경하지 않는다.
- `total_pending_count`는 아래 두 값을 합산한다.
  - 현재 사용자가 host인 크루의 `mission_log.certification_status = PENDING_REVIEW` 개수
  - 현재 사용자가 host인 크루의 `crew_participant.status = PENDING` 개수
- 응답은 배지 표시 목적의 합계만 제공한다. `pending_review_count`, `pending_application_count` 같은 breakdown은 이 API에 포함하지 않는다.
- 0건이면 `total_pending_count = 0`을 반환한다. 배지를 숨길지 여부는 프론트엔드가 결정한다.
- 방장 이력이 없어도 200 OK와 `total_pending_count = 0`을 반환한다. 운영 메뉴 노출 판단은 `GET /api/me`의 `is_host_ever`, `hosted_crew_count`를 사용한다.
- 운영 콘솔 목록, 필터, 승인/거절 액션에 필요한 데이터는 별도 방장 운영탭 API에서 조회한다.
- 알림 목록 배지의 unread count와 이 API의 운영 대기 건수는 서로 다른 UX 신호다. 알림 unread count는 `GET /api/notifications/unread-count` 또는 `GET /api/me/activity-summary`의 `activity_info.unread_notification_count`를 사용한다.
- 내부 `member.id`, participant 내부 ID, `crew_participant_id`, `mission_log_id`, 내부 FK, `reject_memo`, raw moderation chain은 노출하지 않는다.

**Error**

- `UNAUTHORIZED`
- `MEMBER_NOT_FOUND`

---

## `PATCH /api/me/profile`

> 닉네임, 프로필 이미지, 상태 메시지를 수정한다.

**Request**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `nickname` | `string` | N | 노출 이름 |
| `profile_image_s3_key` | `string \| null` | N | 프로필 이미지 S3 key. `null`이면 이미지 제거 |
| `status_message` | `string \| null` | N | 상태 메시지 (최대 100자). `null`이면 제거 |

**Response** `200 OK`

```json
{
  "member_uuid": "018f4fd2-6d7a-7a41-9f58-6d07f5c3c901",
  "email": "user@example.com",
  "nickname": "돈독러",
  "profile_image_url": "https://cdn.example.com/profile/018f4fd2-6d7a-7a41-9f58-6d07f5c3c901/avatar.jpg",
  "status_message": "오늘도 한 걸음 더",
  "updated_at": "2026-05-01T12:10:00+09:00"
}
```

**Error**

- `INVALID_INPUT`
- `NICKNAME_ALREADY_EXISTS`
- `PROFILE_IMAGE_NOT_FOUND`

**정책**

- 세 필드 중 하나 이상이 요청에 포함되어야 한다.
- PATCH 요청에서 누락된 필드는 기존 값을 유지한다. 예를 들어 `nickname`을 생략하면 기존 닉네임이 유지되고, `profile_image_s3_key`를 생략하면 기존 프로필 이미지가 유지되며 응답의 `profile_image_url`도 기존 이미지 기준으로 내려간다.
- 명시적 `null`은 제거 가능한 필드에 대해서만 삭제 요청으로 처리한다. `profile_image_s3_key`를 `null`로 보내면 프로필 이미지를 제거하며, 응답의 `profile_image_url`은 `null`이다.
- `nickname`은 필수 문자열 필드이므로 명시적 `null`로 삭제할 수 없다. `nickname`을 수정하려면 유효한 문자열을 보내야 하며, `null` 또는 유효하지 않은 값은 `INVALID_INPUT`이다.
- `nickname`은 trim 후 저장하며, 2자 이상 10자 이하이다.
- `nickname`은 앞뒤 공백과 공백-only 값을 허용하지 않는다.
- `nickname`은 기존 사용자 닉네임과 중복될 수 없다.
- 프로필 이미지는 presigned upload로 먼저 업로드된 S3 key만 참조한다.
- `profile_image_url`은 응답 전용 파생 URL이며 PATCH 요청에서 받지 않는다. PATCH 요청에서는 이미지 변경/삭제를 위해 `profile_image_s3_key`만 전달한다.

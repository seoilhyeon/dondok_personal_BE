# 미션 인증

## `POST /api/uploads/presigned-url`

> S3 직접 업로드를 위한 presigned URL을 발급한다.

**Request**

| 필드                  | 타입      | 필수 | 설명                                                |
| --------------------- | --------- | ---- | --------------------------------------------------- |
| `purpose`             | `string`  | Y    | `MISSION_IMAGE`, `PROFILE_IMAGE`, 또는 `CREW_IMAGE` |
| `crew_id`             | `integer` | N    | 미션 이미지 업로드 시 대상 크루                     |
| `crew_participant_id` | `integer` | N    | 미션 이미지 업로드 시 대상 참여자                   |
| `content_type`        | `string`  | Y    | 이미지 content type                                 |
| `content_length`      | `integer` | Y    | 파일 크기 (bytes)                                   |

**Response** `200 OK`

| 필드         | 타입     | 설명                          |
| ------------ | -------- | ----------------------------- |
| `upload_url` | `string` | 단기 TTL presigned upload URL |
| `s3_key`     | `string` | 서버가 생성한 S3 object key   |
| `expires_at` | `string` | URL 만료 시각                 |

**Error**

- `ALREADY_CERTIFIED_TODAY`
- `CERTIFICATION_IN_REVIEW`

**정책**

- S3 object key는 서버가 생성한다. 클라이언트가 임의 path를 지정할 수 없다.
- S3 bucket/object는 private이다. presigned URL은 upload delegation 수단이지 validation delegation 수단이 아니다.
- 이미지 종류별 권장 key 형식:
  - 미션 이미지: `mission/{crewId}/{crewParticipantId}/{uuid}` (`crewParticipantId`는 `crew_participant.id`)
  - 프로필 이미지: `profile/{memberUuid}/{uuid}`
  - 크루 대표 이미지: `crew/{memberUuid}/{uuid}` (`memberUuid`는 발급 요청자, 즉 미래의 호스트)
- `purpose = CREW_IMAGE`는 크루 생성/수정 흐름에서 사용되며 `crew_id`/`crew_participant_id`를 요구하지 않는다. 발급 자체는 settlement/lifecycle authority가 아니다.
- 발급 시점에 사용자/크루/참여자 권한을 검증한다.
- `purpose = MISSION_IMAGE`인 경우, 당일(`server_time` 기준 `Asia/Seoul` 날짜의 cadence slot) `certification_status = SUCCESS`인 인증 로그가 존재하면 `ALREADY_CERTIFIED_TODAY`, `PENDING_REVIEW`인 인증 로그가 존재하면 `CERTIFICATION_IN_REVIEW`를 반환한다.
- **이 검사는 UX pre-check(최선 시도)이며 certification authority가 아니다.** 경쟁 상태나 presigned 발급 이후 상태 변경으로 인해 pre-check를 통과한 요청이 `POST /api/mission-logs`에서 거절될 수 있다. 최종 authoritative guard는 `POST /api/mission-logs`다.
- 클라이언트는 발급받은 URL로 S3에 직접 업로드한 뒤, `s3_key`로 미션 로그 생성을 요청한다.

---

## `POST /api/mission-logs`

> 업로드된 이미지로 미션 인증 로그를 생성한다.

**Request**

| 필드           | 타입      | 필수 | 설명                        |
| -------------- | --------- | ---- | --------------------------- |
| `crew_id`      | `integer` | Y    | 대상 크루                   |
| `image_s3_key` | `string`  | Y    | 업로드 완료된 이미지 S3 key |
| `caption`      | `string`  | Y    | 인증 텍스트 (5~100자)       |

**Response** `201 Created`

```json
{
  "mission_log_id": 9001,
  "crew_id": 42,
  "crew_participant_id": 101,
  "image_url": "https://cdn.example.com/mission/9001.jpg",
  "image_s3_key": "mission/42/101/9001.jpg",
  "caption": "오늘도 미션 완료했습니다",
  "image_hash": "9b74c9897bac770ffc029102a200c5de8c0e9e5b9d3c9c7e5f4f5c1a2b3c4d5e",
  "server_time": "2026-05-11T05:58:10+09:00",
  "certification_status": "SUCCESS",
  "failure_reason": null,
  "decision_type": null,
  "reject_reason_code": null
}
```

**Error**

- `CREW_NOT_FOUND`
- `PARTICIPANT_NOT_FOUND`
- `PARTICIPANT_WITHDRAWN`
- `PARTICIPANT_NOT_ELIGIBLE`
- `ALREADY_CERTIFIED_TODAY`
- `CERTIFICATION_IN_REVIEW`
- `NOT_MISSION_DAY`

**정책**

- 인증 시점에는 crew 단위 Redisson 락을 기본으로 사용하지 않는다.
- 인증은 `MissionLog` 원본 보존이 우선이다.
- `LOCKED` 상태인 참여자만 인증을 제출할 수 있다. `PENDING` 등 비`LOCKED` 상태에서는 `PARTICIPANT_NOT_ELIGIBLE`을 반환한다.
- `SPECIFIC_DAYS` 크루에서 `server_time` 기준 해당 요일이 `mission_schedule_days`에 포함되지 않으면 `NOT_MISSION_DAY`를 반환하고 제출 자체를 거절한다. 로그를 생성한 뒤 정산에서 exclude하는 방식은 사용하지 않는다.
- **재업로드 정책**: 당일(`server_time` 기준 `Asia/Seoul` 날짜의 cadence slot) 인증 상태에 따라 아래와 같이 처리한다.
  - `SUCCESS` 로그가 있으면 `ALREADY_CERTIFIED_TODAY`를 반환하고 거절한다. `ALREADY_CERTIFIED_TODAY`는 `DAILY`/`SPECIFIC_DAYS` 구분 없이 동일 cadence slot의 기인증 완료 상태를 의미한다.
  - `PENDING_REVIEW` 로그가 있으면 `CERTIFICATION_IN_REVIEW`를 반환하고 거절한다.
  - `FAILED` 로그만 있으면 재업로드를 허용한다.
- 이미지 업로드 자체는 별도 presigned upload 계약으로 처리하고, 이 API는 업로드 완료된 `image_s3_key`와 필수 `caption`을 함께 받는다.
- 유효한 mission-log creation에는 서버가 검증한 `image_s3_key`와 5~100자 `caption`이 모두 필요하다. image-only 또는 caption-only 인증 생성은 허용하지 않는다.
- `image_url`은 조회/서빙용 nullable URL이며, 이미지 존재/범위 검증의 기준은 `image_s3_key`와 서버의 S3 object validation이다.
- `caption`은 feed/display/replay evidence 용도이고 단독 인증 성공/실패, 정산, 원장 기준이 아니다.
- Presigned URL은 upload delegation 수단이지 validation delegation 수단이 아니다.
- 서버는 `image_s3_key`가 현재 사용자/participant/crew 범위에 속하는지 검증한다.
- 서버는 S3 object를 직접 조회해 존재 여부, size, content-type, ownership, EXIF를 검증한다.
- 클라이언트는 `exif_taken_at`을 authoritative source로 제출하지 않는다.
- 서버는 S3 object에서 EXIF/hash 등 risk signal을 추출하고 가능한 범위에서 검증한다.
- `image_hash`는 서버가 S3 object 바이트에서 직접 계산한 SHA-256 hex 값이다. 클라이언트가 제출한 hash를 신뢰하지 않고, 요청 body로도 받지 않는다. fraud/duplicate detection signal이며 authority가 아니다.
- `MissionLog.exif_taken_at`은 서버가 추출한 보조 metadata 저장값이며 authoritative timing source가 아니다.
- EXIF 부재나 이상은 단독 automatic failure가 아니라 fraud/risk signal이다. 필요한 경우 moderation/review flow로 라우팅한다.
- 정산 인정 판단의 timing anchor는 `server_time` 기준으로 수행한다.
- `server_time`은 서버가 인증 요청을 수신한 시각이다.
- `certification_status`는 인증 요청의 resolved certification state를 나타내며, 최종 정산 인정 여부를 보장하지 않는다.
- `certification_status` 결정 시 아래 조건을 검토한다.
  - 업로드 object의 소유/범위/기본 무결성
  - EXIF/hash risk signal과 review 필요 여부
  - 미션 기간 내 요청 여부 (`server_time` 기준)
  - frozen baseline / participant 상태 적합성
- `certification_status = SUCCESS`는 인증 성공 표시이며, 최종 정산에서 인정된다는 의미는 아니다.
- `certification_status = FAILED`여도 원본 로그는 저장할 수 있다.
- `certification_status = PENDING_REVIEW`는 업로드 직후 검수/판정 대기 상태다.
- `certification_status`는 인증 피드 badge, dashboard projection, 알림 input에 쓰이는 resolved state이며 EXIF/hash raw signal이나 host moderation `decision_type`/`reject_reason_code`와 동일 axis로 해석하지 않는다.
- `mission_log.failure_reason`은 인증 시점 실패 사유(system/timing axis)다.
- `decision_type`, `reject_reason_code`는 호스트 검수자 결과 axis이며 시스템 `failure_reason`과 의미 vocabulary가 다르다.
- POST 응답에서 `decision_type`, `reject_reason_code`는 검수가 일어나지 않은 시점에는 `null`이다. `reject_memo`는 participant-facing 응답 필드가 아니다.
- `settlement_item.calculation_reason`은 정산 시점 포함/제외 근거다.
- MVP 인증 API에서 `OUT_OF_SCHEDULE`는 사용하지 않는다.
- 최종 정산에서의 인정 여부는 `certification_status`가 아니라 Settlement 계산 단계에서 결정된다.
- 인증은 성공했지만 정산에서 제외되는 경우(예: 동일 cadence slot 내 중복 인정)는 `mission_log.failure_reason`이 아니라 `settlement_item.calculation_reason`으로만 표현한다. `SPECIFIC_DAYS` 비해당 요일은 제출 시점에 `NOT_MISSION_DAY`로 거절되므로 settlement exclude 대상이 아니다.
- 인증 시점 성공 로그도 최종 정산에서 제외될 수 있다.

---

## `GET /api/crews/{crewId}/mission-logs/me`

> 특정 크루에서 내 미션 인증 로그 목록을 조회한다.

**Query**

| 필드     | 타입      | 필수 | 설명 |
| -------- | --------- | ---- | ---- |
| `cursor` | `string`  | N    | 이전 응답의 `next_cursor`로 다음 slice를 조회한다. |
| `limit`  | `integer` | N    | 기본 20, 최대 100. |

**Response** `200 OK`

```json
{
  "items": [
    {
      "mission_log_id": 9001,
      "crew_participant_id": 101,
      "image_url": "https://cdn.example.com/mission/9001.jpg",
      "caption": "오늘도 미션 완료했습니다",
      "image_hash": "9b74c9897bac770ffc029102a200c5de8c0e9e5b9d3c9c7e5f4f5c1a2b3c4d5e",
      "server_time": "2026-05-11T05:58:10+09:00",
      "exif_taken_at": "2026-05-11T05:57:58+09:00",
      "certification_status": "SUCCESS",
      "failure_reason": null,
      "decision_type": null,
      "reject_reason_code": null
    }
  ],
  "next_cursor": null
}
```

**Error**

- `CREW_NOT_FOUND`
- `PARTICIPANT_NOT_FOUND`

**정책**

- 이 API는 원시 인증 기록 조회용이다.
- 정산 인정 판단 기준 시간은 `MissionLog.server_time`이다.
- `exif_taken_at`은 서버가 S3 object에서 추출/검증한 촬영 시각 보조 정보이며, 최종 정산 인정 시각 기준으로 사용하지 않는다.
- `image_hash`는 서버 계산 SHA-256 결과의 read-only 노출이며, 동일 인증 사진 중복 의심 신호일 뿐 authority가 아니다.
- `certification_status`는 인증 요청의 resolved certification state(`PENDING_REVIEW`/`SUCCESS`/`FAILED`)이며, 정산에서 인정된 횟수를 나타내는 값이 아니다.
- `decision_type`, `reject_reason_code`는 현재 latest-effective 검수 결과 projection이다. 참여자-facing 응답은 `reject_reason_code`만 제공하고 `reject_memo`를 포함하지 않는다. `reject_memo`는 internal/private context다.
- FE는 이 값을 `최종 성공 횟수` 또는 `정산 인정 횟수`로 사용하면 안 된다.
- 최종 인정 여부와 인정 횟수는 반드시 정산 결과 API `GET /api/settlements/{settlementId}`를 기준으로 판단해야 한다.

---

## `GET /api/crews/{crewId}/moderation-logs`

> 크루 내 인증 검수 이력을 조회한다.

**Query**

| 필드             | 타입      | 필수 | 설명                |
| ---------------- | --------- | ---- | ------------------- |
| `mission_log_id` | `integer` | N    | 특정 인증 로그 필터 |
| `cursor`         | `string`  | N    | 페이지네이션 커서   |
| `limit`          | `integer` | N    | 기본 50, 최대 200   |

**Response** `200 OK`

```json
{
  "items": [
    {
      "moderation_history_id": 7001,
      "mission_log_id": 9001,
      "before_state": { "decision_type": null },
      "after_state": { "decision_type": "MANUAL_APPROVE" },
      "decision_type": "MANUAL_APPROVE",
      "reject_reason_code": null,
      "moderator_member_uuid": "018f4fd2-6d7a-7a41-9f58-6d07f5c3c901",
      "changed_at": "2026-05-12T11:00:00+09:00"
    }
  ],
  "next_cursor": null
}
```

**Error**

- `CREW_NOT_FOUND`
- `FORBIDDEN_NOT_HOST`

**정책**

- 본 API는 읽기 전용 감사 조회 전용이다. 검수 결정을 새로 만들거나 수정하지 않는다.
- `moderation_history`는 추가만 가능하다. 본 API는 기존 레코드를 변경/삭제하지 않는다.
- 호출자가 해당 크루의 host여야 한다. host가 아니면 `FORBIDDEN_NOT_HOST`를 반환한다.
- 크루원이 본인의 검수 결과를 확인할 때는 `GET /api/me/verification-history`를 사용한다.
- `decision_type`은 `MANUAL_APPROVE`, `MANUAL_REJECT`, `AUTO_APPROVE`, `AUTO_REJECT`만 사용한다.
- `reject_reason_code`는 `TIME_VIOLATION`, `DUPLICATE`, `MISSION_MISMATCH`, `UNCLEAR`, `INAPPROPRIATE`, `OTHER`만 사용한다.
- `reject_memo`는 일반적으로 nullable이지만 `OTHER`일 때 필수이며 최대 50자다. 내부 전용 컨텍스트이므로 참여자 응답에는 포함하지 않는다. `OTHER`여도 참여자는 raw memo text가 아니라 `reject_reason_code`만 받는다.
- `before_state`, `after_state`는 검수 결정 시점의 최신 유효 스냅샷 JSON이다. 정산 결과를 재계산하는 입력으로 사용하지 않는다.
- 검수자 식별은 `moderator_member_uuid`로만 노출한다. 내부 FK `moderator_id`는 응답에 포함하지 않는다.
- 이 API는 운영 관리자 권한 엔드포인트가 아니다. MVP에서는 관리자 수정 워크플로를 추가하지 않는다.

---

## `POST /api/mission-logs/{missionLogId}/moderation/approve`

> 방장이 검수 대기 중인 인증을 승인한다.

**Request** body 없음

**Response** `200 OK`

```json
{
  "mission_log_id": 9001,
  "crew_id": 42,
  "crew_participant_id": 101,
  "certification_status": "SUCCESS",
  "decision_type": "MANUAL_APPROVE",
  "reject_reason_code": null,
  "decided_at": "2026-05-12T11:00:00+09:00",
  "moderation_history_id": 7001
}
```

**Error**

- `MISSION_LOG_NOT_FOUND`
- `FORBIDDEN_NOT_HOST`
- `MISSION_LOG_NOT_REVIEWABLE`
- `SETTLEMENT_INPUT_FROZEN`

**정책**

- 호출자가 해당 미션 로그가 속한 크루의 host여야 한다.
- `PENDING_REVIEW` 상태인 인증 로그만 승인 가능하다. 이미 `SUCCESS`/`FAILED`이면 `MISSION_LOG_NOT_REVIEWABLE`로 거절한다.
- 정산 입력이 freeze된 이후에는 `SETTLEMENT_INPUT_FROZEN`으로 거절한다.
- 승인 시 `certification_status`가 `SUCCESS`로 전환된다.
- `moderation_history`에 `MANUAL_APPROVE` 기록이 append-only로 추가된다.
- 이 API는 settlement 재계산을 trigger하지 않는다. `point_history`, `settlement_item`을 직접 변경하지 않는다.

---

## `POST /api/mission-logs/{missionLogId}/moderation/reject`

> 방장이 검수 대기 중인 인증을 거절한다.

**Request**

| 필드                 | 타입     | 필수 | 설명                                                                                           |
| -------------------- | -------- | ---- | ---------------------------------------------------------------------------------------------- |
| `reject_reason_code` | `string` | Y    | `TIME_VIOLATION`, `DUPLICATE`, `MISSION_MISMATCH`, `UNCLEAR`, `INAPPROPRIATE`, `OTHER` 중 하나 |
| `reject_memo`        | `string` | N    | 거절 사유 메모. `OTHER`일 때 필수, 최대 50자                                                   |

**Response** `200 OK`

```json
{
  "mission_log_id": 9001,
  "crew_id": 42,
  "crew_participant_id": 101,
  "certification_status": "FAILED",
  "decision_type": "MANUAL_REJECT",
  "reject_reason_code": "MISSION_MISMATCH",
  "decided_at": "2026-05-12T11:10:00+09:00",
  "moderation_history_id": 7002
}
```

**Error**

- `MISSION_LOG_NOT_FOUND`
- `FORBIDDEN_NOT_HOST`
- `MISSION_LOG_NOT_REVIEWABLE`
- `SETTLEMENT_INPUT_FROZEN`
- `INVALID_REJECT_REASON_CODE`
- `REJECT_MEMO_REQUIRED`
- `REJECT_MEMO_TOO_LONG`

**정책**

- 호출자가 해당 미션 로그가 속한 크루의 host여야 한다.
- `PENDING_REVIEW` 상태인 인증 로그만 거절 가능하다. 이미 `SUCCESS`/`FAILED`이면 `MISSION_LOG_NOT_REVIEWABLE`로 거절한다.
- 정산 입력이 freeze된 이후에는 `SETTLEMENT_INPUT_FROZEN`으로 거절한다.
- `reject_reason_code`는 `TIME_VIOLATION`, `DUPLICATE`, `MISSION_MISMATCH`, `UNCLEAR`, `INAPPROPRIATE`, `OTHER` 중 하나여야 한다. 그 외 값은 `INVALID_REJECT_REASON_CODE`로 거절한다.
- `reject_reason_code = OTHER`이면 `reject_memo`가 필수이며 최대 50자다. 누락 시 `REJECT_MEMO_REQUIRED`, 초과 시 `REJECT_MEMO_TOO_LONG`을 반환한다.
- `reject_memo`는 내부 기록용이며 참여자 응답에는 포함하지 않는다. 참여자는 `reject_reason_code`만 확인할 수 있다.
- `moderation_history`에 `MANUAL_REJECT` 기록이 append-only로 추가된다.
- 이 API는 settlement 재계산을 trigger하지 않는다. 거절은 인증 입력 결정이며 즉시 환급/원장 변경이 아니다.

---

## `GET /api/me/verification-history`

> 내 미션 인증 검증 결과 현황을 조회한다. 내가 제출한 인증들의 상태(`PENDING_REVIEW`, `SUCCESS`, `FAILED`)를 크루별로 확인할 수 있다.

**Query**

| 필드      | 타입      | 필수 | 설명                                              |
| --------- | --------- | ---- | ------------------------------------------------- |
| `crew_id` | `integer` | N    | 특정 크루로 범위를 좁힌다                         |
| `role`    | `string`  | N    | `participant` 또는 `host`. 생략 시 `participant`  |
| `status`  | `string`  | N    | `PENDING_REVIEW`, `SUCCESS`, `FAILED` 필터        |
| `cursor`  | `string`  | N    | 이전 응답의 `next_cursor`로 다음 slice를 조회한다 |

**Response** `200 OK`

```json
{
  "items": [
    {
      "verification_history_item_id": "participant:9001",
      "perspective": "participant",
      "crew_id": 42,
      "crew_title": "새벽 기상 챌린지",
      "mission_log_id": 9001,
      "occurred_at": "2026-05-11T05:58:10+09:00",
      "verification_status": "SUCCESS",
      "reject_reason_code": null,
      "signal_summary": {
        "exif": "NORMAL",
        "reviewer": "HOST"
      },
      "links": {
        "feed": "/api/crews/42/feed",
        "settlement": null
      }
    }
  ],
  "next_cursor": null
}
```

**Error**

- `CREW_NOT_FOUND`
- `PARTICIPANT_NOT_FOUND`
- `FORBIDDEN`

**정책**

- 기본(`role` 생략 또는 `role = participant`)은 본인이 제출한 인증 로그 summary만 반환한다.
- `role = host`이면 본인이 방장으로 검수한 활동 summary를 반환한다. 호스트가 아닌 크루에 대한 정보는 포함하지 않는다.
- `role = host&crew_id={id}`로 조회 시 해당 크루의 host가 아니면 `FORBIDDEN`을 반환한다.
- `reject_reason_code`는 participant-facing 거절 사유이며, `reject_memo`는 internal/private context이므로 응답에 포함하지 않는다.
- `verification_status`는 현재 resolved 상태(`PENDING_REVIEW`/`SUCCESS`/`FAILED`)다. `SUCCESS`는 인증 상태 요약이지 최종 정산 인정을 보장하지 않는다.

---

## `GET /api/me/mission-feed`

> 내 크루별 인증 활동 타임라인을 조회한다. 내가 참여 중인 모든 크루의 인증 기록을 최신순으로 조회한다.

**Query**

| 필드      | 타입      | 필수 | 설명                                                                          |
| --------- | --------- | ---- | ----------------------------------------------------------------------------- |
| `crew_id` | `integer` | N    | 특정 크루로 범위를 좁힌다. 호출자가 참여자인 크루여야 한다                    |
| `status`  | `string`  | N    | `PENDING_REVIEW`, `SUCCESS`, `FAILED` 필터. `NOT_SUBMITTED`는 허용하지 않는다 |
| `cursor`  | `string`  | N    | 이전 응답의 `next_cursor`로 다음 slice를 조회한다                             |
| `limit`   | `integer` | N    | 기본 20, 최대 100                                                             |

**Response** `200 OK`

```json
{
  "items": [
    {
      "mission_log_id": 9101,
      "crew_id": 42,
      "crew_title": "새벽 기상 챌린지",
      "crew_participant_id": 101,
      "image_url": "https://cdn.example.com/mission/9101.jpg",
      "caption": "오늘 미션 인증합니다",
      "server_time": "2026-05-12T06:05:00+09:00",
      "certification_status": "SUCCESS",
      "reject_reason_code": null,
      "reaction_counts": { "👏": 2, "🔥": 1 },
      "my_reactions": ["👏"],
      "links": {
        "crew_feed": "/api/crews/42/feed"
      }
    },
    {
      "mission_log_id": 9003,
      "crew_id": 42,
      "crew_title": "새벽 기상 챌린지",
      "crew_participant_id": 101,
      "image_url": "https://cdn.example.com/mission/9003.jpg",
      "caption": "재업로드 후 다시 검토를 기다리고 있습니다",
      "server_time": "2026-05-11T07:10:02+09:00",
      "certification_status": "PENDING_REVIEW",
      "reject_reason_code": null,
      "reaction_counts": {},
      "my_reactions": [],
      "links": {
        "crew_feed": "/api/crews/42/feed"
      }
    }
  ],
  "next_cursor": "2026-05-11T07:10:02+09:00_9003"
}
```

**Error**

- `CREW_NOT_FOUND`
- `PARTICIPANT_NOT_FOUND`
- `INVALID_FEED_STATUS_FILTER`

**정책**

- 본인이 제출한 인증 로그만 포함된다. cross-crew append-only mission activity timeline이다.
- 모든 `certification_status`(`PENDING_REVIEW`, `SUCCESS`, `FAILED`)가 기본 포함되며, `status` 파라미터로 필터링할 수 있다.
- `NOT_SUBMITTED`는 `mission_log` row가 없는 synthetic slot projection이므로 이 API에 포함하지 않는다.
- 최신순(`server_time DESC`) 정렬이며, 같은 날짜에 여러 시도가 있으면 각 시도가 별도 item으로 노출된다. (`FAILED`/`PENDING_REVIEW` 재업로드 이력 보존)
- `reaction_counts`와 `my_reactions`는 `GET /api/crews/{crewId}/feed`와 동일한 범위로 제공된다. `certification_status = SUCCESS`인 item에만 값이 있으며, `FAILED`/`PENDING_REVIEW` item은 빈 map/빈 list로 응답된다.
- `reject_reason_code`는 participant-facing 거절 사유이며, `reject_memo`는 internal/private context이므로 응답에 포함하지 않는다.
- `SUCCESS` item이라도 최종 정산 인정을 보장하지 않는다. 최종 인정 여부는 `settlement_item.calculation_reason`을 기준으로 판단한다.

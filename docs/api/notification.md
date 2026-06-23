# 알림

> FCM(Firebase Cloud Messaging)을 통한 Android-first 알림이 기준이다. 알림 서비스는 canonical state authority가 아닌 **best-effort re-entry hint** 역할만 한다. FE는 알림 payload의 값(display_text, resource_id 등)을 최종 상태로 신뢰하지 않아야 한다. 알림(push 또는 inbox item) 클릭 시 클라이언트는 `deep_link`로 이동 후 canonical REST API를 refetch해야 한다.

## `POST /api/notifications/devices`

> FCM 알림 수신을 위해 기기를 등록한다.

**Request**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `platform` | `string` | Y | `ANDROID`, `WEB` |
| `fcm_token` | `string` | Y | FCM 토큰 |
| `device_id` | `string` | Y | 클라이언트 기기 식별자 |
| `app_version` | `string` | N | 앱 버전 |

**Response** `201 Created`

```json
{
  "device_id": "client-generated-or-installation-id",
  "platform": "ANDROID",
  "enabled": true,
  "created_at": "2026-05-07T09:00:00+09:00"
}
```

**정책**

- JWT `sub = member.uuid`로 현재 인증 사용자의 디바이스만 등록한다.
- 동일 사용자의 같은 `device_id`가 이미 있으면 새 row를 만들지 않고 `fcm_token`, `app_version`을 갱신한다.

---

## `PATCH /api/notification-devices/{deviceId}` — 예정 기능

> 등록된 기기의 FCM 토큰 또는 앱 버전을 갱신한다.

> MVP 현재 구현에는 아직 포함되지 않았다. 일정 확정 후 추가한다.

**Request**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `fcm_token` | `string` | N | 갱신할 FCM 토큰 |
| `app_version` | `string` | N | 앱 버전 |

**Response** `200 OK`

**Error**

- `DEVICE_NOT_FOUND`

**정책**

- `fcm_token` 또는 `app_version` 중 최소 하나는 제공되어야 한다. 둘 다 없으면 `INVALID_INPUT`을 반환한다.
- 제공되지 않은 필드는 기존 값을 유지한다.

---

## `DELETE /api/notification-devices/{deviceId}` — 예정 기능

> 기기 알림 등록을 삭제한다.

> MVP 현재 구현에는 아직 포함되지 않았다. 일정 확정 후 추가한다.

**Response** `204 No Content`

---

## `GET /api/notification-settings`

> 내 알림 설정을 조회한다. 설정이 없으면 기본값(전체 허용)을 반환한다.

**Response** `200 OK`

```json
{
  "categories": {
    "EMOJI_REACTION": true,
    "HOST_VERIFICATION": true,
    "DEADLINE_APPROACHING": true,
    "DAILY_RESULT": true,
    "SETTLEMENT": true,
    "CREW_DISBANDED": true,
    "CREW_NEWS": true
  },
  "quiet_start_time": null,
  "quiet_end_time": null
}
```

**정책**

- JWT `sub = member.uuid`로 현재 인증 사용자의 설정만 조회한다.
- `quiet_start_time`, `quiet_end_time`은 `HH:mm` 형식 문자열 또는 `null`이다.

---

## `PATCH /api/notification-settings`

> 카테고리별 알림 토글과 방해금지 시간을 저장한다.

**Request**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `categories` | `object` | N | 카테고리별 허용 여부. 값은 `true` 또는 `false` |
| `quiet_start_time` | `string \| null` | N | 방해금지 시작 시간. `HH:mm` 형식 |
| `quiet_end_time` | `string \| null` | N | 방해금지 종료 시간. `HH:mm` 형식 |

**카테고리**

- `EMOJI_REACTION`
- `HOST_VERIFICATION`
- `DEADLINE_APPROACHING`
- `DAILY_RESULT`
- `SETTLEMENT`
- `CREW_DISBANDED`
- `CREW_NEWS`

**Response** `200 OK`

```json
{
  "categories": {
    "EMOJI_REACTION": true,
    "HOST_VERIFICATION": true,
    "DEADLINE_APPROACHING": false,
    "DAILY_RESULT": true,
    "SETTLEMENT": true,
    "CREW_DISBANDED": true,
    "CREW_NEWS": true
  },
  "quiet_start_time": "22:00",
  "quiet_end_time": "08:00"
}
```

**Error**

- `INVALID_QUIET_HOURS`

**정책**

- `quiet_start_time`, `quiet_end_time`은 둘 다 제공하거나 둘 다 `null`로 해제해야 한다.
- 둘 중 하나만 설정하면 `INVALID_QUIET_HOURS`를 반환한다.
- 요청에 포함되지 않은 방해금지 시간 필드는 기존 값을 유지한다.

---

## `GET /api/notifications`

> 내 알림 목록을 조회한다.

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
      "notification_id": "uuid",
      "event_type": "MISSION_LOG_VERIFICATION_RESULT",
      "resource_type": "mission_log",
      "resource_id": "9001",
      "deep_link": "dondok://crews/42/mission-logs/9001",
      "occurred_at": "2026-05-13T07:31:08+09:00",
      "display_text": "인증 결과가 반영되었습니다.",
      "crew_name": "새벽 기상 챌린지",
      "requires_refetch": true,
      "read_at": null
    }
  ],
  "next_cursor": null
}
```

**알림 항목 필드 정책**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `notification_id` | `string (uuid)` | 알림 고유 식별자 |
| `event_type` | `string` | 이벤트 유형 코드. 클라이언트 라우팅/UI 분기 기준 |
| `resource_type` | `string` | 연관 리소스 유형 (`mission_log`, `crew`, `settlement` 등) |
| `resource_id` | `string` | 연관 리소스 식별자 |
| `deep_link` | `string` | 알림 클릭 시 이동할 앱 내 URL scheme |
| `occurred_at` | `string (ISO-8601)` | 이벤트 발생 시각 |
| `display_text` | `string` | 서버가 생성한 알림 표시 문구. UI 직접 렌더링용 참고값 |
| `crew_name` | `string \| null` | 연관 크루 이름. 크루 연관이 없으면 null |
| `requires_refetch` | `boolean` | 클릭 후 canonical REST API refetch 필요 여부. **MVP에서는 항상 `true`로 취급한다** |
| `read_at` | `string \| null` | 읽음 처리 시각. 미읽음이면 `null` |

**정책**

- 최신순(`occurred_at DESC, notification_id DESC`) 정렬.
- `next_cursor`는 다음 slice가 존재할 때만 응답에 포함하며, 없거나 `null`이면 더 조회할 slice가 없다.
- `read_at = null`이면 읽지 않은 알림이다.

**click → refetch 계약**

알림(push 수신 또는 inbox item) 클릭 시 클라이언트는 다음 순서로 처리한다:

1. `deep_link` URL scheme으로 이동한다.
2. `deep_link`가 가리키는 리소스에 대해 canonical REST API를 refetch한다.
3. 알림 payload(`display_text`, `resource_id` 등)를 최종 상태 값으로 사용하지 않는다.

`requires_refetch: true`는 이 계약이 항상 적용됨을 나타낸다. MVP에서는 모든 알림이 `true`이므로, 클라이언트는 이 값의 `false` 분기를 MVP 단계에서 구현할 필요가 없다.

**event_type 목록**

> 구현 시 추가·변경될 수 있다.

| `event_type` | `resource_type` | 설명 |
| --- | --- | --- |
| `MISSION_LOG_VERIFICATION_RESULT` | `mission_log` | 방장이 인증 로그를 검수한 결과 (승인 또는 거절) |
| `CREW_APPLICATION_APPROVED` | `crew` | 내 크루 참여 신청이 승인됨 |
| `CREW_APPLICATION_REJECTED` | `crew` | 내 크루 참여 신청이 거절됨 |
| `CREW_ACTIVATED` | `crew` | 크루가 활성화(미션 시작)됨 |
| `SETTLEMENT_COMPLETED` | `settlement` | 크루 정산이 완료됨 |

---

## `GET /api/notifications/unread-count`

> 읽지 않은 알림 수를 조회한다.

**Response** `200 OK`

```json
{
  "unread_count": 3
}
```

---

## `PATCH /api/notifications/{notificationId}/read`

> 알림을 읽음 처리한다.

**Response** `200 OK`

---

## `PATCH /api/notifications/read-all`

> 알림을 전체 읽음 처리한다.

**Request** body 없음

**Response** `200 OK`

```json
{
  "updated_count": 5
}
```

**정책**

- 현재 인증 사용자의 읽지 않은 알림 전체를 읽음 처리한다.
- 이미 읽은 알림은 변경하지 않는다.
- `updated_count`는 실제로 읽음 처리된 알림 수다.

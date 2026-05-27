# 알림

> FCM을 통한 Android-first 알림이 기준이다. 알림 payload는 canonical state authority가 아니며, 알림 클릭 시 클라이언트는 `deep_link`로 이동 후 canonical REST API를 refetch해야 한다.

## `POST /api/notification-devices`

FCM 알림 수신을 위해 기기를 등록한다.

**Request**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `platform` | `string` | Y | `ANDROID` |
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

---

## `PATCH /api/notification-devices/{deviceId}`

등록된 기기의 FCM 토큰 또는 앱 버전을 갱신한다.

**Request**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `fcm_token` | `string` | N | 갱신할 FCM 토큰 |
| `app_version` | `string` | N | 앱 버전 |

**Response** `200 OK`

---

## `DELETE /api/notification-devices/{deviceId}`

기기 알림 등록을 삭제한다.

**Response** `204 No Content`

---

## `GET /api/notifications`

내 알림 목록을 조회한다.

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
      "requires_refetch": true,
      "read_at": null
    }
  ],
  "next_cursor": null
}
```

---

## `GET /api/notifications/unread-count`

읽지 않은 알림 수를 조회한다.

**Response** `200 OK`

```json
{
  "unread_count": 3
}
```

---

## `PATCH /api/notifications/{notificationId}/read`

알림을 읽음 처리한다.

**Response** `200 OK`

---

## `PATCH /api/notifications/read-all`

알림을 전체 읽음 처리한다.

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

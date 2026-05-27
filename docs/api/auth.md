# 인증 / 회원

## `POST /api/auth/signup`

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
- `VALIDATION_ERROR`

**정책**

- `email`과 `nickname`은 unique다.
- 가입 직후 자동 로그인 여부는 본 명세에서 고정하지 않는다. MVP 기본 흐름은 가입 후 로그인이다.

---

## `POST /api/auth/login`

> 이메일과 비밀번호로 로그인하여 JWT 토큰을 발급한다.

**Request**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `email` | `string` | Y | 로그인 식별자 |
| `password` | `string` | Y | 비밀번호 원문 |

**Response** `200 OK`

```json
{
  "access_token": "{accessToken}",
  "token_type": "Bearer",
  "expires_in": 1800,
  "member": {
    "member_uuid": "018f4fd2-6d7a-7a41-9f58-6d07f5c3c901",
    "email": "user@example.com",
    "nickname": "돈독러"
  }
}
```

```http
Set-Cookie: refreshToken={refreshToken}; Path=/; Max-Age=604800; HttpOnly; SameSite=Lax
```

**Error**

- `INVALID_CREDENTIALS`
- `MEMBER_DEACTIVATED`

**정책**

- JWT `sub`는 `member.uuid`다. `email`이나 `member.id`를 subject로 사용하지 않는다.
- refresh token은 `HttpOnly` + `Secure` + `SameSite` cookie로만 전달한다. response body, `localStorage`, `sessionStorage`, JS 접근 대상으로 노출하지 않는다.
- refresh token은 서버에 hash로 저장한다.

---

## `POST /api/auth/refresh`

> refresh token으로 access token을 재발급한다.

**Request** body 없음. 브라우저/클라이언트가 자동 전송하는 refresh token cookie(`HttpOnly`, `Secure`, `SameSite`)를 서버가 읽는다.

**Response** `200 OK`

```json
{
  "access_token": "new-access-token"
}
```

rotation 정책에 따라 새 refresh token이 발급되는 경우 `Set-Cookie` 헤더로 갱신한다.

```http
Set-Cookie: refreshToken={newRefreshToken}; Path=/; HttpOnly; Secure; SameSite=Lax
```

**Error**

- `REFRESH_TOKEN_INVALID`
- `REFRESH_TOKEN_EXPIRED`
- `REFRESH_TOKEN_REVOKED`

**정책**

- 재발급은 refresh cookie 기반이며, request body로 refresh token을 받지 않는다.
- 새 refresh token도 `Set-Cookie`로만 재발급한다 (rotate). token 값을 response body에 포함하지 않는다.

---

## `POST /api/auth/logout`

> refresh token을 폐기하여 로그아웃한다.

**Request** body 없음. 인증이 필요한 API로, `Authorization: Bearer {accessToken}` 헤더와 함께 클라이언트가 자동 전송하는 refresh token cookie를 서버가 읽어 revoke 처리한다.

**Response** `204 No Content`

```http
Set-Cookie: refreshToken=; Path=/; Max-Age=0; HttpOnly; Secure; SameSite=Lax
```

**Error**

- `REFRESH_TOKEN_INVALID`

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

- 수정 가능 필드: `nickname`, `profile_image_url`, `status_message`
- `profile_image_url`은 저장된 `member.profile_image_s3_key`에서 파생한 접근 URL이며, 이미지가 없으면 null일 수 있다.
- `status_message`는 자유 입력 한 줄 상태 메시지다(최대 100자).
- `is_host_ever`, `hosted_crew_count`는 read-time 계산 projection이다.

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
  "is_host_ever": true,
  "hosted_crew_count": 2,
  "status": "ACTIVE",
  "updated_at": "2026-05-01T12:10:00+09:00"
}
```

**Error**

- `VALIDATION_ERROR`
- `NICKNAME_ALREADY_EXISTS`
- `PROFILE_IMAGE_NOT_FOUND`

**정책**

- 세 필드 중 하나 이상이 요청에 포함되어야 한다.
- 프로필 이미지는 presigned upload로 먼저 업로드된 S3 key만 참조한다.

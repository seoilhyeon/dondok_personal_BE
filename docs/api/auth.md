# 인증

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
Set-Cookie: refreshToken={refreshToken}; Path=/; Max-Age=604800; HttpOnly; Secure; SameSite=Lax
```

**Error**

- `INVALID_INPUT`
- `INVALID_CREDENTIALS`
- `MEMBER_DEACTIVATED`

**정책**

- JWT `sub`는 `member.uuid`다. `email`이나 `member.id`를 subject로 사용하지 않는다.
- refresh token은 `HttpOnly` + `Secure` + `SameSite` cookie로만 전달한다. response body, `localStorage`, `sessionStorage`, JS 접근 대상으로 노출하지 않는다.
- 저장소 정책: 발급된 Refresh Token은 서버측 저장소에 원문이 아닌 해시 형태로 저장하여 관리한다. (최종 저장소 구현체는 인프라 구성에 따라 결정된다.)

---

## Google OAuth 로그인

> Google 인증 코드 교환, Google access token 발급, 사용자 정보 조회, 회원가입/로그인은 모두 백엔드가 처리한다.

### OAuth 시작 URL

프론트는 Google SDK를 사용하지 않고 아래 백엔드 URL로 브라우저를 이동시킨다.

```http
GET /oauth2/authorization/google
```

### 성공 redirect

Google OAuth 인증이 성공하면 백엔드는 회원 조회/가입/연결을 처리한 뒤 프론트 성공 페이지로 redirect한다.

```http
{OAUTH2_SUCCESS_REDIRECT_URI}?code={loginCode}
```

- `code`: access token 교환에 사용하는 1회용 로그인 코드
- login code TTL: 3분
- login code는 1회 사용 후 즉시 만료된다.
- login code 저장소에는 토큰 원문이 아니라 `memberUuid`만 저장한다.

### 실패 redirect

Google OAuth 인증 또는 회원 처리에 실패하면 백엔드는 프론트 실패 페이지로 redirect한다.

```http
{OAUTH2_FAILURE_REDIRECT_URI}?reason={errorCode}
```

주요 `reason`:

- `oauth_email_not_verified`
- `oauth_account_conflict`
- `member_deactivated`
- `oauth_login_failed`

---

## `POST /api/auth/oauth2/token`

> OAuth 성공 redirect로 받은 1회용 code를 access token으로 교환한다.

**Request**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `code` | `string` | Y | OAuth 성공 redirect query parameter로 받은 1회용 로그인 코드 |

```json
{
  "code": "{loginCode}"
}
```

**Response** `200 OK`

```json
{
  "access_token": "{accessToken}",
  "token_type": "Bearer",
  "expires_in": 1800,
  "member": {
    "member_uuid": "018f4fd2-6d7a-7a41-9f58-6d07f5c3c901",
    "email": "user@example.com",
    "nickname": "nickname"
  }
}
```

token exchange 성공 시 백엔드는 이 시점에 refresh token을 발급하고 HttpOnly cookie로 전달한다.

```http
Set-Cookie: refreshToken={refreshToken}; Path=/; Max-Age=604800; HttpOnly; Secure; SameSite=Lax
```

**Error**

- `OAUTH_LOGIN_CODE_INVALID`
- `INVALID_CREDENTIALS`
- `MEMBER_DEACTIVATED`

**정책**

- Google OAuth callback 응답에서는 access token과 refresh token을 직접 전달하지 않는다.
- refresh token은 `/api/auth/oauth2/token` 응답의 `Set-Cookie`로만 전달한다.
- access token은 response body로 전달하고, 이후 API 호출 시 `Authorization: Bearer {accessToken}` 형식으로 사용한다.
- 프론트는 token exchange 요청 시 refresh token cookie 처리를 위해 `credentials: include`를 사용해야 한다.

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

재발급 시 Refresh Token Rotation(RTR) 정책에 따라 새로운 토큰이 발급되면 쿠키를 갱신한다.

```http
Set-Cookie: refreshToken={newRefreshToken}; Path=/; Max-Age=604800; HttpOnly; Secure; SameSite=Lax
```

**Error**

- `REFRESH_TOKEN_INVALID`
- `REFRESH_TOKEN_EXPIRED`
- `MEMBER_DEACTIVATED`

**정책**

- 재발급은 refresh cookie 기반이며, request body로 refresh token을 받지 않는다.
- 갱신 (Rotation): 보안을 위해 재발급 시 서버에 저장된 기존 토큰 정보를 새로운 토큰 정보로 교체(Update)하여 일회성(Single-use)을 보장한다.
- 새 refresh token도 `Set-Cookie`로만 재발급한다. token 값을 response body에 포함하지 않는다.


---

## `POST /api/auth/logout`

> access token 인증을 기준으로 클라이언트 세션을 종료하고, 필요한 경우 서버 저장소의 refresh token을 폐기한다.

**Request** body 없음. 인증이 필요한 API로, `Authorization: Bearer {accessToken}` 헤더가 필요하다. 클라이언트가 자동 전송하는 refresh token cookie가 있으면 서버는 해당 토큰 정보를 찾아 revoke 처리한다.

**Response** `204 No Content`

```http
Set-Cookie: refreshToken=; Path=/; Max-Age=0; HttpOnly; Secure; SameSite=Lax
```

**Error**

- `UNAUTHORIZED`: access token이 없거나 유효하지 않은 경우


**정책**
- 로그아웃은 access token 인증을 기준으로 수행한다.
- refresh token cookie는 서버 저장소 revoke를 위한 보조 정보로 사용한다.
- refresh token cookie가 존재하고, 서버 저장소에서 인증 사용자와 일치하는 token row를 찾은 경우 revoke 처리한다.
- refresh token cookie가 없거나, 만료됐거나, 이미 revoke됐거나, 서버 저장소에 없거나, 인증 사용자와 일치하지 않는 경우에도 로그아웃은 성공 처리하며 해당 refresh token row는 변경하지 않는다.
- 성공 응답은 항상 refresh token cookie 삭제를 위한 `Set-Cookie`를 포함한다.
- 폐기(Revoke): 서버 저장소에서 인증 사용자와 일치하는 Refresh Token 정보를 찾은 경우 즉시 무효화하여 재사용을 방지한다.

---
**참고(Notes)**

- 현재 리프레시 토큰은 RDBMS 기반으로 관리되나, 성능 및 세션 관리 효율화를 위해 향후 Redis-backed 저장소로 이관될 예정입니다.
- 쿠키의 `Secure` 속성은 환경 설정에 따라 적용된다. 운영 환경에서는 `Secure=true` 사용을 원칙으로 한다.
- cookie 기반 인증 endpoint(`/api/auth/refresh`, `/api/auth/logout`)는 CSRF 방어를 위해 허용된 `Origin` 또는 `Referer`에서 온 요청만 처리한다.

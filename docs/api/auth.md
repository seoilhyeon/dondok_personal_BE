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
Set-Cookie: refreshToken={refreshToken}; Path=/; Max-Age=604800; HttpOnly; SameSite=Lax
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
Set-Cookie: refreshToken={newRefreshToken}; Path=/; HttpOnly; Secure; SameSite=Lax
```

**Error**

- `REFRESH_TOKEN_INVALID`
- `REFRESH_TOKEN_EXPIRED`

**정책**

- 재발급은 refresh cookie 기반이며, request body로 refresh token을 받지 않는다.
- 갱신 (Rotation): 보안을 위해 재발급 시 서버에 저장된 기존 토큰 정보를 새로운 토큰 정보로 교체(Update)하여 일회성(Single-use)을 보장한다.
- 새 refresh token도 `Set-Cookie`로만 재발급한다. token 값을 response body에 포함하지 않는다.


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


**정책**
- 폐기 (Revoke): 로그아웃 시 서버에 저장된 해당 사용자의 Refresh Token 정보를 즉시 삭제/무효화하여 재사용을 방지한다.

---
**참고(Notes)**
- 현재 리프레시 토큰은 RDBMS 기반으로 관리되나, 성능 및 세션 관리 효율화를 위해 향후 Redis-backed 저장소로 이관될 예정입니다.
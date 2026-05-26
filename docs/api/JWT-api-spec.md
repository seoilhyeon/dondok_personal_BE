# API Spec

## 인증 헤더 규칙

### 토큰 전달 방식

| 토큰 | 전달 위치 | 형식 | 사용 목적 |
| --- | --- | --- | --- |
| Access Token | 요청 헤더 `Authorization` | `Bearer {accessToken}` | 보호된 API 요청 인증 |
| Refresh Token | 쿠키 `refreshToken` | HttpOnly Cookie | Access Token 만료 시 자동 재발급 |

### 요청 헤더 규칙

보호된 API를 호출할 때는 Access Token을 `Authorization` 헤더에 담아 전송한다.

```http
Authorization: Bearer {accessToken}
```

- `Bearer`와 토큰 사이에는 공백 한 칸을 둔다.
- `Authorization` 헤더가 없거나 `Bearer ` 접두사가 없으면 Access Token이 없는 요청으로 처리된다.
- Access Token에는 `type=access` 클레임이 있어야 한다.
- Refresh Token은 `Authorization` 헤더로 보내지 않는다. Refresh Token은 서버가 발급한 `refreshToken` 쿠키로만 전송한다.

### 로그인 응답 규칙

`POST /api/auth/login` 성공 시 서버는 Access Token을 응답 바디로 내려주고, Refresh Token은 `Set-Cookie` 헤더로 내려준다.

```json
{
  "accessToken": "{accessToken}",
  "tokenType": "Bearer",
  "accessTokenExpiresIn": 3600
}
```

```http
Set-Cookie: refreshToken={refreshToken}; Path=/; Max-Age=1209600; HttpOnly; SameSite=Lax
```

- Access Token 만료 시간은 현재 설정 기준 3600초(1시간)이다.
- Refresh Token 만료 시간은 현재 설정 기준 1209600초(14일)이다.
- 개발 환경에서는 `refreshToken` 쿠키의 `Secure=false`, `SameSite=Lax` 설정을 사용한다.
- 운영 환경에서 크로스 사이트 쿠키 전송이 필요하면 `Secure=true`, `SameSite=None` 설정을 사용한다.

### 인증 필요한 API 호출 예시

```http
GET /api/members/me HTTP/1.1
Authorization: Bearer {accessToken}
Cookie: refreshToken={refreshToken}
```

일반적인 보호 API 호출에는 `Authorization` 헤더가 필수이다. 브라우저 환경에서는 `refreshToken` 쿠키가 자동으로 포함될 수 있도록 credentials 옵션을 함께 사용한다.

```js
fetch("/api/members/me", {
  headers: {
    Authorization: `Bearer ${accessToken}`
  },
  credentials: "include"
});
```

### Access Token 만료 시 재발급 규칙

요청에 포함된 Access Token이 만료된 경우, 서버는 `refreshToken` 쿠키를 확인해 토큰을 자동 재발급한다.

자동 재발급 조건은 다음과 같다.

- `Authorization` 헤더에 만료된 Access Token이 `Bearer {accessToken}` 형식으로 포함되어 있다.
- Access Token의 `type` 클레임이 `access`이다.
- 요청 쿠키에 `refreshToken`이 포함되어 있다.
- Refresh Token이 유효하고 `type=refresh` 클레임을 가진다.
- 서버 DB에 저장된 Refresh Token 해시와 요청 Refresh Token이 일치한다.
- 토큰의 회원이 존재하며 상태가 `ACTIVE`이다.

재발급에 성공하면 서버는 기존 요청을 인증된 요청으로 처리하고, 응답 헤더로 새 Access Token과 새 Refresh Token 쿠키를 내려준다.

```http
Authorization: Bearer {newAccessToken}
Set-Cookie: refreshToken={newRefreshToken}; Path=/; Max-Age=1209600; HttpOnly; SameSite=Lax
```

클라이언트는 응답의 `Authorization` 헤더가 존재하면 저장 중인 Access Token을 새 값으로 교체한다.

### 로그아웃 규칙

`POST /api/auth/logout`은 인증이 필요한 API이다. 요청 시 Access Token을 `Authorization` 헤더에 포함해야 한다.

```http
POST /api/auth/logout HTTP/1.1
Authorization: Bearer {accessToken}
Cookie: refreshToken={refreshToken}
```

로그아웃 성공 시 서버는 저장된 Refresh Token을 삭제하고, `refreshToken` 쿠키를 만료시킨다.

```http
Set-Cookie: refreshToken=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax
```

### 인증 예외 API

다음 API는 `Authorization` 헤더 없이 호출할 수 있다.

| Method | Path | 설명 |
| --- | --- | --- |
| POST | `/api/auth/login` | 로그인 |
| POST | `/api/members/signup` | 회원가입 |

그 외 API는 기본적으로 인증이 필요하다.

### CORS 관련 헤더

현재 서버는 프론트엔드 출처 `http://localhost:3000`을 허용한다.

- 허용 메서드: `GET`, `POST`, `PATCH`, `PUT`, `DELETE`, `OPTIONS`
- 허용 요청 헤더: 전체 허용
- 노출 응답 헤더: `Authorization`, `Set-Cookie`
- 쿠키 인증을 위해 credentials 요청을 허용한다.

브라우저 클라이언트는 Refresh Token 쿠키 송수신을 위해 요청에 credentials 옵션을 포함해야 한다.

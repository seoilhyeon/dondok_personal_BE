# API 명세: Dondok MVP

## 공통 규칙

### Base URL

```
/api
```

### 인증

#### 토큰 전달 방식

| 토큰          | 전달 위치                 | 형식                   | 사용 목적                        |
| ------------- | ------------------------- | ---------------------- | -------------------------------- |
| Access Token  | 요청 헤더 `Authorization` | `Bearer {accessToken}` | 보호된 API 요청 인증             |
| Refresh Token | 쿠키 `refreshToken`       | HttpOnly Cookie        | `POST /api/auth/refresh` 명시적 호출 시 쿠키로 자동 전송 |

#### 요청 헤더 규칙

보호된 API를 호출할 때는 Access Token을 `Authorization` 헤더에 담아 전송한다.

```http
Authorization: Bearer {accessToken}
```

- `Bearer`와 토큰 사이에는 공백 한 칸을 둔다.
- `Authorization` 헤더가 없거나 `Bearer ` 접두사가 없으면 Access Token이 없는 요청으로 처리된다.
- Access Token에는 `type=access` 클레임이 있어야 한다.
- Refresh Token은 `Authorization` 헤더로 보내지 않는다. Refresh Token은 서버가 발급한 `refreshToken` 쿠키로만 전송한다.

#### 로그인 응답 규칙

`POST /api/auth/login` 성공 시 서버는 Access Token을 응답 바디로 내려주고, Refresh Token은 `Set-Cookie` 헤더로 내려준다.

```json
{
  "access_token": "{accessToken}",
  "token_type": "Bearer",
  "expires_in": 3600,
  "member": {
    "member_uuid": "018f4fd2-6d7a-7a41-9f58-6d07f5c3c901",
    "email": "user@example.com",
    "nickname": "돈독러"
  }
}
```

```http
Set-Cookie: refreshToken={refreshToken}; Path=/; Max-Age=1209600; HttpOnly; SameSite=Lax
```

- Access Token 만료 시간은 현재 설정 기준 3600초(1시간)이다.
- Refresh Token 만료 시간은 현재 설정 기준 1209600초(14일)이다.
- 개발 환경에서는 `refreshToken` 쿠키의 `Secure=false`, `SameSite=Lax` 설정을 사용한다.
- 운영 환경에서 크로스 사이트 쿠키 전송이 필요하면 `Secure=true`, `SameSite=None` 설정을 사용한다.

#### 인증 필요한 API 호출 예시

```http
GET /api/me HTTP/1.1
Authorization: Bearer {accessToken}
Cookie: refreshToken={refreshToken}
```

일반적인 보호 API 호출에는 `Authorization` 헤더가 필수이다. Access Token은 FE 메모리에서 관리하며 요청마다 헤더에 직접 포함한다. Credentials 전략(쿠키 자동 전송 등)은 implementation detail로 두며, `POST /api/auth/refresh` · `POST /api/auth/logout` 등 쿠키 수신이 필요한 endpoint는 각 endpoint 명세를 따른다.

#### Access Token 재발급

Access Token이 만료되면 클라이언트는 `POST /api/auth/refresh`를 명시적으로 호출하여 새 Access Token을 발급받는다.

- Refresh Token은 `HttpOnly` 쿠키로만 전달하며, request body로 보내지 않는다.
- 재발급 성공 시 새 `access_token`을 response body로 반환한다. 필요 시 새 Refresh Token을 `Set-Cookie`로 rotate한다.
- 재발급 실패(`REFRESH_TOKEN_INVALID`, `REFRESH_TOKEN_EXPIRED`, `REFRESH_TOKEN_REVOKED`) 시 클라이언트는 로그인 화면으로 유도한다.
- 서버 미들웨어가 자동으로 refresh하거나 응답 `Authorization` 헤더로 새 Access Token을 전달하는 방식은 사용하지 않는다.

#### 로그아웃 규칙

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

#### 인증 예외 API

다음 API는 `Authorization` 헤더 없이 호출할 수 있다.

| Method | Path                  | 설명     |
| ------ | --------------------- | -------- |
| POST   | `/api/auth/login`     | 로그인   |
| POST   | `/api/auth/signup`    | 회원가입 |

그 외 API는 기본적으로 인증이 필요하다.

#### CORS 관련 헤더

현재 서버는 프론트엔드 출처 `http://localhost:3000`을 허용한다.

- 허용 메서드: `GET`, `POST`, `PATCH`, `PUT`, `DELETE`, `OPTIONS`
- 허용 요청 헤더: 전체 허용
- 노출 응답 헤더: `Authorization`, `Set-Cookie`
- 쿠키 인증을 위해 credentials 요청을 허용한다.

브라우저 클라이언트는 Refresh Token 쿠키 송수신을 위해 요청에 credentials 옵션을 포함해야 한다.

### 식별자

| 식별자        | 용도                                                            |
| ------------- | --------------------------------------------------------------- |
| `member.uuid` | 외부 API 식별자. JWT `sub`, 외부 사용자 식별자로 사용한다.      |
| `member.id`   | DB 내부 FK 전용. API 응답 및 JWT에 사용하지 않는다.             |
| `email`       | 로그인 식별자. routing identity, JWT subject로 사용하지 않는다. |

### 시간

- 모든 시간 값은 offset 포함 ISO-8601 문자열로 주고받는다.
- 예: `2026-05-07T00:05:00+09:00`
- 미션 기간과 정산 판단 기준 시간대는 `Asia/Seoul`로 고정한다.

### 금액

- 모든 금액 필드는 `integer` 원 단위다.

### 에러 응답

모든 4xx/5xx 응답은 아래 형식을 따른다.

```json
{
  "code": "ERROR_CODE",
  "message": "설명",
  "timestamp": "2026-05-07T00:05:00+09:00"
}
```

---

## Enum

### CrewStatus

| 값           | 설명                                                   |
| ------------ | ------------------------------------------------------ |
| `RECRUITING` | 모집 중. `recruitment_deadline` 전 신청/승인/예치 가능 |
| `ACTIVE`     | 진행 중. 시스템이 `start_at`에 자동 전환               |
| `CLOSED`     | 정상 종료                                              |
| `CANCELLED`  | 시작 전 취소                                           |

### ParticipantStatus

| 값          | 설명                                |
| ----------- | ----------------------------------- |
| `PENDING`   | 가입 신청 완료, 보증금 reserve 상태. capacity reservation에 포함하나 activation eligibility, frozen baseline, settlement 대상은 아님 |
| `LOCKED`    | 방장 승인으로 reserve 확정된 참여 상태. 크루 생성 시 호스트는 별도 신청 없이 이 상태로 자동 생성됨. activation eligibility, frozen participant baseline, settlement eligibility의 anchor |
| `REJECTED`  | 방장 거절. reserve는 `CREW_RESERVE_RELEASE`로 반환 |
| `CANCELLED` | 사용자 신청 취소 (승인 전 `PENDING`만). reserve는 `CREW_RESERVE_RELEASE`로 반환. terminal이 아님: 동일 crew에 재신청(reopen) 가능. reopen 시 기존 row를 `CANCELLED → PENDING`으로 in-place 복귀 |
| `EXPIRED`   | 시작 전까지 처리되지 않아 자동 만료. reserve는 `CREW_RESERVE_RELEASE`로 반환 |

### SettlementStatus

| 값           | 설명                                                     |
| ------------ | -------------------------------------------------------- |
| `NONE`       | Settlement row 없음 (API projection 전용, DB 저장 안 함) |
| `PENDING`    | 생성됨, 실행 전                                          |
| `RUNNING`    | 실행 중                                                  |
| `SUCCEEDED`  | 완료                                                     |
| `FAILED`     | 실패                                                     |
| `RETRY_WAIT` | 재시도 대기                                              |

### 기타 Enum

| Enum                         | 값                                                                                                                         |
| ---------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| `FrequencyType`              | `DAILY`, `SPECIFIC_DAYS`                                                                                                   |
| `SettlementType`             | `NORMAL`, `CANCELLED_BEFORE_START`                                                                                         |
| `PointTransactionType`       | `POINT_CHARGE`, `CREW_DEPOSIT_RESERVE`, `CREW_RESERVE_RELEASE`, `CREW_SETTLEMENT_REFUND`                                   |
| `DailySettlementType`        | `A` (인증마감 09:00 / 정산 12:00), `B` (인증마감 21:00 / 정산 00:00), `C` (인증마감 23:59 / 정산 익일 12:00)               |
| `MissionLogDecisionType`     | `MANUAL_APPROVE`, `MANUAL_REJECT`, `AUTO_APPROVE`, `AUTO_REJECT`                                                           |
| `MissionLogRejectReasonCode` | `TIME_VIOLATION`, `DUPLICATE`, `MISSION_MISMATCH`, `UNCLEAR`, `INAPPROPRIATE`, `OTHER`                                     |
| `SettlementFailureCode`      | `INPUT_LOAD_FAILED`, `CALCULATION_FAILED`, `POINT_CREDIT_FAILED`, `DUPLICATE_SETTLEMENT`, `LOCK_ACQUIRE_FAILED`, `UNKNOWN` |
| `PointHistoryReferenceType`  | `POINT_CHARGE`, `CREW_PARTICIPANT`, `SETTLEMENT_ITEM`                                                                      |

---

## API 목록

| 도메인      | Method   | Path                                                           | 설명                              |
| ----------- | -------- | -------------------------------------------------------------- | --------------------------------- |
| 인증/회원   | `POST`   | `/api/auth/signup`                                             | 회원가입                          |
| 인증/회원   | `POST`   | `/api/auth/login`                                              | 로그인                            |
| 인증/회원   | `POST`   | `/api/auth/refresh`                                            | access token 재발급               |
| 인증/회원   | `POST`   | `/api/auth/logout`                                             | 로그아웃                          |
| 인증/회원   | `GET`    | `/api/me`                                                      | 내 계정/프로필 조회               |
| 인증/회원   | `PATCH`  | `/api/me/profile`                                              | 내 프로필 수정                    |
| 크루/참여   | `GET`    | `/api/crews`                                                   | 크루 목록 조회                    |
| 크루/참여   | `POST`   | `/api/crews`                                                   | 크루 생성                         |
| 크루/참여   | `GET`    | `/api/crews/{crewId}`                                          | 크루 상세 조회                    |
| 크루/참여   | `POST`   | `/api/crews/{crewId}/participants`                             | 크루 가입 신청                    |
| 크루/참여   | `DELETE` | `/api/crews/{crewId}/participants/me`                          | 가입 신청 취소                    |
| 크루/참여   | `GET`    | `/api/crews/{crewId}/applications`                             | 가입 신청 목록 조회 (방장)        |
| 크루/참여   | `POST`   | `/api/crews/{crewId}/applications/{crewParticipantId}/approve` | 방장 승인                         |
| 크루/참여   | `POST`   | `/api/crews/{crewId}/applications/{crewParticipantId}/reject`  | 방장 거절                         |
| 크루/참여   | `GET`    | `/api/crews/{crewId}/members`                                  | 크루 멤버 목록 조회               |
| 크루 공지   | `GET`    | `/api/crews/{crewId}/notices`                                  | 공지 목록 조회 (후보)             |
| 크루 공지   | `POST`   | `/api/crews/{crewId}/notices`                                  | 방장 공지 작성 (후보)             |
| 크루 공지   | `PATCH`  | `/api/crews/{crewId}/notices/{noticeId}`                       | 방장 공지 수정 (후보)             |
| 크루 공지   | `DELETE` | `/api/crews/{crewId}/notices/{noticeId}`                       | 공지 표시 상태 삭제 (후보)        |
| 크루 공지   | `GET`    | `/api/crews/{crewId}/notices/{noticeId}/comments`              | 공지 댓글 목록 (후보)             |
| 크루 공지   | `POST`   | `/api/crews/{crewId}/notices/{noticeId}/comments`              | 공지 댓글 작성 (후보)             |
| 크루 공지   | `PATCH`  | `/api/crews/{crewId}/notices/{noticeId}/comments/{commentId}`  | 공지 댓글 수정 (후보)             |
| 크루 공지   | `DELETE` | `/api/crews/{crewId}/notices/{noticeId}/comments/{commentId}`  | 댓글 표시 상태 삭제 (후보)        |
| 크루 공지   | `POST`   | `/api/crews/{crewId}/notices/{noticeId}/reactions`             | 공지 리액션 upsert (후보)         |
| 크루 공지   | `DELETE` | `/api/crews/{crewId}/notices/{noticeId}/reactions/me`          | 내 공지 리액션 삭제 (후보)        |
| 미션 인증   | `POST`   | `/api/uploads/presigned-url`                                   | 이미지 업로드 presigned URL 발급  |
| 미션 인증   | `POST`   | `/api/mission-logs`                                            | 인증 제출                         |
| 미션 인증   | `GET`    | `/api/crews/{crewId}/mission-logs/me`                          | 내 인증 기록 조회                 |
| 미션 인증   | `GET`    | `/api/me/verification-history`                                 | 내 검증 결과 현황 조회            |
| 미션 인증   | `GET`    | `/api/me/mission-feed`                                         | 내 크루별 인증 활동 타임라인 조회 |
| 미션 인증   | `GET`    | `/api/crews/{crewId}/moderation-logs`                          | 방장 검수 이력 조회 (방장 전용)   |
| 미션 인증   | `POST`   | `/api/mission-logs/{missionLogId}/moderation/approve`          | 방장 검수 승인                    |
| 미션 인증   | `POST`   | `/api/mission-logs/{missionLogId}/moderation/reject`           | 방장 검수 거절                    |
| 피드/리액션 | `GET`    | `/api/crews/{crewId}/feed`                                     | 인증 피드 조회                    |
| 피드/리액션 | `POST`   | `/api/mission-logs/{missionLogId}/reactions`                   | 리액션 추가                       |
| 피드/리액션 | `DELETE` | `/api/mission-logs/{missionLogId}/reactions/me`                | 리액션 삭제                       |
| 대시보드    | `GET`    | `/api/crews/{crewId}/dashboard`                                | 진행 현황 및 예상 환급 조회       |
| 정산        | `GET`    | `/api/crews/{crewId}/settlement`                               | 방 기준 정산 상태 조회            |
| 정산        | `GET`    | `/api/settlements/{settlementId}`                              | 정산 결과 상세 조회               |
| AI          | `POST`   | `/api/ai/mission-recommendations`                              | AI 크루 생성 도우미               |
| 알림        | `POST`   | `/api/notification-devices`                                    | FCM 디바이스 등록 (후보)          |
| 알림        | `PATCH`  | `/api/notification-devices/{deviceId}`                         | FCM 토큰 갱신 (후보)              |
| 알림        | `DELETE` | `/api/notification-devices/{deviceId}`                         | FCM 디바이스 비활성화 (후보)      |
| 알림        | `GET`    | `/api/notifications`                                           | 알림 목록 조회 (후보)             |
| 알림        | `GET`    | `/api/notifications/unread-count`                              | 미읽음 알림 수 조회 (후보)        |
| 알림        | `PATCH`  | `/api/notifications/{notificationId}/read`                     | 알림 읽음 처리 (후보)             |
| 알림        | `PATCH`  | `/api/notifications/read-all`                                  | 전체 읽음 처리 (후보)             |
| 포인트      | `POST`   | `/api/points/charges`                                          | 포인트 충전                       |
| 포인트      | `GET`    | `/api/points`                                                  | 포인트 잔액 조회                  |
| 포인트      | `GET`    | `/api/points/history`                                          | 포인트 내역 조회                  |

---

## 상태 흐름

### Crew

```
RECRUITING ──(start_at 시스템 자동 활성화)──▶ ACTIVE ──▶ CLOSED
RECRUITING ──(start_at eligibility 미충족)──▶ CANCELLED
```

### Participant

```
PENDING ──(host 승인)──▶ LOCKED
PENDING ──(본인 취소)──▶ CANCELLED
PENDING ──(host 거절)──▶ REJECTED
PENDING ──(자동 만료)──▶ EXPIRED
CANCELLED ──(재신청 reopen)──▶ PENDING
```

- `REJECTED` / `EXPIRED`: terminal. 동일 crew 재신청은 `APPLICATION_NOT_ALLOWED`로 차단.
- host auto-created `LOCKED` row는 reopen 경로에 포함되지 않는다.

### Settlement

```
NONE → PENDING → RUNNING → SUCCEEDED
                  RUNNING → RETRY_WAIT → RUNNING → SUCCEEDED / FAILED
```

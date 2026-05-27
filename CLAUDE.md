# CLAUDE.md — Dondok Backend 코드 작성 가이드

> AI(Claude 등)가 이 레포에서 코드를 작성하거나 수정할 때 반드시 따라야 할 규칙 모음.

---

## 프로젝트 한 줄 요약

Java 17 + Spring Boot 3.2 기반 RESTful API 서버.  
지분 기반 습관 형성 플랫폼 **Dondok**의 백엔드입니다.

---

## 패키지 구조 규칙

도메인별 레이어드 구조를 따른다.

```
domain/{도메인}/
├── controller/   # API 엔드포인트
├── dto/
│   ├── request/  # 요청 DTO
│   └── response/ # 응답 DTO
├── entity/       # JPA 엔티티
├── repository/   # DB 접근 (JPA + QueryDSL)
└── service/      # 비즈니스 로직
```

- 새 도메인 추가 시 위 구조를 그대로 따른다.
- 전역 공통 로직은 `global/` 에 넣는다.
- 외부 인프라 연동(S3, FCM, OpenAI 등)은 `infra/` 에 넣는다.

---

## 핵심 규칙

### 1. API 응답

- 모든 4xx/5xx 에러 응답은 아래 형식을 따른다.

```json
{
  "code": "ERROR_CODE",
  "message": "설명",
  "timestamp": "2026-05-07T00:05:00+09:00"
}
```

- 시각 값은 반드시 `Asia/Seoul` offset 포함 ISO-8601 문자열로 반환한다.
- 금액 필드는 모두 `integer` 원 단위다.
- 응답 필드명은 `snake_case`를 사용한다.

### 2. 식별자

- 외부 API 응답에서 사용자 식별자는 반드시 `member_uuid`를 사용한다.
- DB 내부 FK/join용 `member.id` (Long)는 API 응답에 노출하지 않는다.
- JWT subject(`sub`)는 `member.uuid`다.

```java
// ✅
return new MemberResponse(member.getUuid(), member.getNickname(), ...);

// ❌
return new MemberResponse(member.getId(), member.getNickname(), ...);
```

### 3. 금액 처리

- 금액은 `BigDecimal`로 계산하고, API 응답은 `integer`로 반환한다.
- 지분율은 `RoundingMode.FLOOR`, `Decimal(10, 6)` 기준이다.
- 정산 환급금은 `BIGINT` 원 단위로 저장한다.

```java
// ✅
BigDecimal shareRatio = successCount.divide(totalSuccess, 6, RoundingMode.FLOOR);

// ❌
double shareRatio = successCount / totalSuccess;
```

### 4. 트랜잭션

- 포인트 원장(`point_history`) 생성과 `point_account.balance` 갱신은 반드시 같은 트랜잭션에서 처리한다.
- `point_history`는 append-only다. 기존 레코드를 수정/삭제하지 않는다.
- 정산 재시도 시 중복 지급 방지는 `idempotency_key` + DB unique 제약으로 처리한다.

### 5. 인증/보안

- Access Token 만료: 30분
- Refresh Token 만료: 7일 (Redis 저장, Rotation 정책)
- Refresh Token은 raw value가 아니라 hash로 저장한다.
- `@PreAuthorize` 또는 서비스 레이어에서 권한 검증 후 처리한다.

### 6. QueryDSL

- 복잡한 동적 쿼리는 QueryDSL을 사용한다. JPQL 문자열 직접 작성 금지.
- N+1 방지를 위해 fetch join 또는 `Projections`로 DTO 직접 매핑한다.

```java
// ✅
queryFactory
    .select(Projections.constructor(CrewDto.class, crew.id, crew.title))
    .from(crew)
    .where(crew.status.eq(CrewStatus.RECRUITING))
    .fetch();
```

### 7. S3 이미지 업로드

Presigned URL 흐름을 따른다. 서버를 통한 직접 업로드 금지.

```
1. 클라이언트 → POST /api/uploads/presigned-url → 서버가 S3 key 생성 + URL 발급
2. 클라이언트 → S3 직접 PUT 업로드
3. 클라이언트 → POST /api/mission-logs (s3_key 포함)
4. 서버 → S3 object 존재/size/EXIF 검증 후 저장
```

- S3 object key는 서버가 생성한다. 클라이언트가 임의 key를 지정하지 못한다.
- 미션 이미지 key 형식: `mission/{crewId}/{crewParticipantId}/{uuid}`

### 8. 정산 배치

- `MissionLog.server_time` 기준으로 인정 여부를 판단한다. `exif_taken_at`은 보조 정보다.
- 정산 계산 입력: `MissionLog` + frozen `LOCKED` participant baseline + resolved certification state
- `Settlement.status = SUCCEEDED` 이후에는 `settlement_item` + `point_history`가 최종 source of truth다.
- 배치 실패 시 3회 자동 재시도 후 Grafana 알람 발송.

---

## 금지 패턴

| 패턴 | 이유 |
|------|------|
| API 응답에 `member.id` (Long) 노출 | 외부 식별자는 `member_uuid` |
| `double`/`float`으로 금액 계산 | 부동소수점 오차 → `BigDecimal` 사용 |
| `point_history` 수정/삭제 | append-only 원장 |
| 서버를 통한 S3 직접 업로드 | Presigned URL 흐름 준수 |
| JPQL 문자열 직접 작성 | QueryDSL 사용 |
| `Settlement.status = SUCCEEDED` 후 재계산 | 스냅샷이 source of truth |



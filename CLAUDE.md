# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## 빌드 / 테스트 명령어

```bash
# 전체 빌드 (Spotless 포맷 검사 + Checkstyle + 테스트 포함)
./gradlew build --no-daemon

# 포맷 자동 적용 (코드 작성 후 build 전에 실행)
./gradlew spotlessApply --no-daemon

# 테스트만 실행
./gradlew test --no-daemon

# 단일 테스트 클래스 실행
./gradlew test --no-daemon --tests "com.oit.dondok.domain.member.service.MemberServiceTest"

# 단일 테스트 메서드 실행
./gradlew test --no-daemon --tests "com.oit.dondok.domain.member.service.MemberServiceTest.signup_success"

# Flyway 마이그레이션 검증 (Testcontainers 필요)
./gradlew flywayValidationTest --no-daemon
```

> 코드 수정 후 항상 `spotlessApply` → `build` 순서로 실행한다.
> `build`는 `spotlessCheck`를 포함하므로 포맷이 맞지 않으면 실패한다.

---

## 아키텍처 개요

### 인증 (JWT)

**Security 설정**: `infra/auth/config/SecurityConfig.java` (JWT 필터 포함)

JWT 인증 흐름:
1. `JwtAuthenticationFilter` (`infra/auth/filter/`) — 요청마다 `Authorization: Bearer <token>` 헤더에서 AccessToken 추출
2. `TokenProvider.parseAccessToken()` → `TokenPayload(memberUuid, issuedAt, expiresAt)` 반환
3. `memberUuid`를 principal로 `UsernamePasswordAuthenticationToken`에 저장
4. Controller에서 `@AuthenticationPrincipal UUID memberUuid`로 꺼냄

```java
// ✅ Controller에서 인증된 사용자 추출
@GetMapping
public ResponseEntity<Foo> foo(@AuthenticationPrincipal UUID memberUuid) { ... }

// ❌ 헤더 직접 사용 — JWT 구현 이전 임시 패턴, 신규 API에 사용 금지
@GetMapping
public ResponseEntity<Foo> foo(@RequestHeader("X-Member-Id") Long memberId) { ... }
```

**인증 예외 경로**: `infra/auth/config/SecurityConfig.java`의 `PERMIT_ALL_PATTERNS` / `GET_PERMIT_ALL_PATTERNS` / `POST_PERMIT_ALL_PATTERNS`는 인증 없이 허용되는 공개 엔드포인트 목록이다(프로파일 분기 없이 항상 적용).

### 포트 패턴

외부 서비스 연동 인터페이스는 `domain/{도메인}/port/`에 둔다.
예: `CrewPointPort` (포인트 락) — `FakeCrewPointPort`는 `@Profile({"local","dev","integration"})`로만 등록.
`prod` 프로파일에서는 실제 구현체가 반드시 존재해야 한다.

이미지 인프라용 stub 구현체는 테스트 컨텍스트 부팅을 위해 `test` 프로파일에 등록한다.
예: `StubImageStorageAdapter`, `StubImageDeliveryAdapter`는 `@Profile("test")`, `DefaultImageObjectKeyPolicy`는 프로파일 무관 단일 빈이다.
storage와 delivery 모두 `test` 외 모든 프로파일(local/dev/prod/integration)에서 동작하는 실제 구현 `S3ImageStorageAdapter` / `S3ImageDeliveryAdapter`(`@Profile("!test")`)와 상호배타다. 즉 어떤 프로파일에서도 각 포트당 정확히 하나의 빈만 등록된다(test=stub, 그 외=실제).
실제 S3 wiring은 `integration-s3` 프로파일 + LocalStack(Testcontainers) 기반 `S3ImageStorageAdapterIntegrationTest`로 검증한다.

### ArchUnit 자동 검증

`ArchitectureRulesTest`가 빌드 시 아래 규칙을 자동 검증한다:
- Entity에 `@Builder`, `@Setter`, `@Data` 금지 / public 생성자 금지
- Entity public JavaBean setter 금지. 단, Entity 자신의 상태와 불변식을 캡슐화하는 도메인 command 메서드는 허용
- Service `find*/get*/read*/search*/count*/exists*` 메서드는 `@Transactional(readOnly=true)` 필수
- Service command 메서드는 `@Transactional` 필수
- Controller → Repository 직접 의존 금지
- Response DTO에 `memberId` 계열 노출 금지
- 허용된 도메인 레이어: `controller`, `service`, `repository`, `entity`, `dto`, `exception`, `port`

### 응답 DTO

- 필드명 snake_case 변환은 `@JsonNaming(SnakeCaseStrategy.class)` 또는 `@JsonProperty("field_name")`
- 시각 값은 `LocalDateTime` 저장, 응답 시 `ZoneId("Asia/Seoul")`로 `OffsetDateTime` 변환

```java
private static OffsetDateTime toSeoulOffset(LocalDateTime ldt) {
  return ldt == null ? null : ldt.atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
}
```

### API 문서

`docs/api/` 에 도메인별 API 스펙이 있다. 새 API 구현 전에 반드시 해당 파일을 먼저 확인한다.
응답 필드 타입·이름·예시값은 `docs/api/{도메인}.md`가 계약 기준이다.

---

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
- 에러코드/Exception은 각 도메인 폴더 안에 둔다.
  예: `domain/crew/exception/`, `domain/auth/exception/`
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
- Refresh Token 만료: 7일 (DB `member_refresh_token` 저장, Rotation 정책)
- Refresh Token은 raw value가 아니라 hash로 저장한다.
- `@PreAuthorize` 또는 서비스 레이어에서 권한 검증 후 처리한다.

### 6. QueryDSL

- 단순 조회는 JPA Repository 메서드 또는 `@Query`로 작성한다.
- 동적 조건 / 복잡한 조인은 QueryDSL을 사용한다. 이 경우 JPQL 문자열 직접 작성 금지.
- N+1 방지를 위해 fetch join 또는 `Projections`로 DTO 직접 매핑한다.

```java
// ✅ 단순 조회 — JPA 메서드 또는 @Query
List<Crew> findByStatus(CrewStatus status);

// ✅ 동적 조건 — QueryDSL
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
| 동적 조건/복잡한 조인에 JPQL 문자열 직접 작성 | QueryDSL 사용 |
| `Settlement.status = SUCCEEDED` 후 재계산 | 스냅샷이 source of truth |
| `throw new RuntimeException()` 직접 사용 | `CustomException + ErrorCode` enum 조합 사용 |
| Entity에 `@Data` 또는 `@Builder` | `@Getter` + 정적 팩토리 메서드 패턴 사용 (ArchUnit 자동 검증) |

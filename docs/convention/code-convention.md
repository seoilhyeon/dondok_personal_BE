# 코드 컨벤션

## 포맷팅

- Java 포맷팅은 `google-java-format` 기준으로 통일한다.
- IDE 개인 설정 대신 formatter 결과를 우선한다.
- `Spotless`를 사용해 포맷 검증을 자동화한다.

## 패키지 구조

도메인별 레이어드 아키텍처를 따른다.

```
domain/{도메인명}/
├── controller/
├── service/
├── repository/
├── entity/
├── exception/
└── dto/
    ├── request/
    └── response/
```

- 도메인별 예외와 에러 코드는 `domain/{도메인명}/exception/` 아래에 둔다.

## 네이밍 규칙

### 기본 네이밍

| 대상 | 규칙 | 예시 |
|------|------|------|
| 클래스 | `PascalCase` | `CrewService` |
| 메서드 / 변수 | `camelCase` | `findCrewById` |
| 상수 | `UPPER_SNAKE_CASE` | `MAX_CREW_SIZE` |
| URL Path | `lowercase` | `/api/crew-members` |
| DB 컬럼 / 테이블 | `snake_case` | `mission_log` |

### 계층별 클래스 네이밍

| 계층 | 접미사 | 예시 |
|------|--------|------|
| Controller | `Controller` | `CrewController` |
| Service | `Service` | `CrewService` |
| Repository | `Repository` | `CrewRepository` |
| Request DTO | `Request` | `CrewCreateRequest` |
| Response DTO | `Response` | `CrewResponse` |

## 객체 생성 규칙

- 도메인 엔티티 / VO는 **정적 팩토리 메서드** 사용을 우선한다.
- Builder는 DTO, 테스트 데이터, 단순 전달 객체에 한해 제한적으로 사용한다.
- Lombok `@Data`, `@Setter` 사용 금지 → `@Getter` + 정적 팩토리 메서드 조합을 사용한다.

## DB 매핑 규칙

- JPA 필드는 `camelCase`로 작성한다.
- Hibernate naming strategy를 사용해 DB 컬럼명은 `snake_case`로 자동 매핑한다.
- 특별한 사유가 없으면 `@Column(name = "...")`을 직접 지정하지 않는다.
- 직접 지정이 필요한 경우에도 `@Column(name)`, `@JoinColumn(name)`, `@Index(columnList)`,
  `@UniqueConstraint(columnNames)`에는 `snake_case`를 사용한다.

## 공통 규칙

- Entity에는 외부 계층 의존, 트랜잭션, IO 로직을 두지 않는다. 단, Entity 자신의 상태와 불변식을 캡슐화하는 도메인 command 메서드는 허용한다.
- Entity public JavaBean setter(`set*`)는 금지하고, 상태 변경은 의도가 드러나는 command 메서드로 표현한다.
- Service의 public `find`, `get`, `read`, `search`, `count`, `exists` 계열 조회 메서드는
  메서드 또는 Service 클래스 수준에서 `@Transactional(readOnly = true)`를 선언한다.
- Service의 public command 메서드는 메서드 또는 Service 클래스 수준에서 쓰기 트랜잭션 경계를 선언한다.
  단, 조회 메서드명에 `orCreate`, `andUpdate`처럼 쓰기 의도가 함께 드러나면 command로 취급한다.
- 금액, 포인트, 정산, 지분율 계산 시 `double` / `float` 사용 금지 → `BigDecimal` 필수
- 의미 없는 주석 금지, 복잡한 로직은 메서드명으로 의도를 표현한다

## 아키텍처 규칙

- Controller는 Repository를 직접 의존하지 않는다.
- Controller endpoint는 Entity를 Response Body로 직접 반환하지 않는다.
  `ResponseEntity<Entity>`, `List<Entity>`처럼 응답 타입 안에 Entity가 포함되는 경우도 금지한다.
  Controller 내부에서 Entity를 DTO로 매핑하기 위한 의존은 허용한다.
- Service는 Controller / Web 계층을 의존하지 않는다.
- Repository는 Service를 의존하지 않는다.
- Entity / 순수 도메인 모델은 Controller, Request DTO, Response DTO를 의존하지 않는다.
- Entity / 순수 도메인 모델은 Config, Security, Web 계층을 의존하지 않는다.
- Domain Exception은 Controller, Service, Repository, Entity, DTO 같은 구현 레이어와
  Config, Security, Web 계층을 의존하지 않는다.

## API 응답 식별자 규칙

- 외부 API 응답에서 사용자 식별자는 `member_uuid`를 사용한다.
- Java Response DTO 필드명은 `memberUuid` 또는 역할 접두사가 붙은 `authorMemberUuid`,
  `hostMemberUuid` 같은 UUID 기반 이름을 사용한다.
- `memberId`, `userId`, `authorMemberId`처럼 내부 DB 식별자로 해석될 수 있는 필드는
  Response DTO에 노출하지 않는다.

## 예외 처리

- `CustomException` + `ErrorCode` enum 조합을 사용한다.
- `GlobalExceptionHandler`에서 일괄 처리한다.
- 에러 응답 형식: `ErrorResponse.error(ErrorCode)`

## API 문서화

- 초기 API 문서는 Swagger / OpenAPI 기반으로 관리한다.
- 핵심 API는 추후 RestDocs 기반 테스트 문서로 보강한다.

## 자동화 및 품질 게이트

- Git hooks를 사용해 커밋 / 푸시 전 기본 검증을 수행한다.
- 주요 아키텍처 규칙은 ArchUnit으로 검증한다.
- 포맷팅, 테스트, 아키텍처 규칙은 자동화된 품질 게이트로 관리한다.
# Backend 기여/온보딩 가이드

이 문서는 Dondok 백엔드 프로젝트에 처음 참여하는 팀원이 로컬 개발 환경을 맞추고,
PR 전에 같은 품질 게이트를 통과하기 위한 최소 절차를 설명합니다.

## 1. 공통 전제

- Java 17을 사용합니다.
- Docker 기반 통합 테스트가 있으므로 Docker Desktop이 실행 중이어야 합니다.
- 로컬 비밀값은 `.env`에만 둡니다. `.env`는 절대 커밋하지 않습니다.
- PR 전에는 포맷, 정적 분석, 테스트, secret 검사를 통과해야 합니다.

## 2. macOS 설정

### 2.1 필수 도구 설치

Homebrew 기준입니다.

```bash
brew install openjdk@17
brew install gitleaks
brew install pre-commit
```

Docker Desktop은 별도로 설치하고 실행합니다.

### 2.2 설치 확인

```bash
java -version
docker --version
gitleaks version
pre-commit --version
```

## 3. Windows 설정

Windows에서는 **PowerShell**과 **Git Bash**를 구분해서 사용합니다.

- 일반 명령/수동 검증: PowerShell 사용 가능
- Git hook 설치/실행: Git for Windows가 제공하는 Git Bash 사용 권장

### 3.1 필수 도구 설치

PowerShell에서 `winget`을 사용할 수 있다면 아래 명령으로 설치합니다.

```powershell
winget install --id EclipseAdoptium.Temurin.17.JDK -e
winget install --id Docker.DockerDesktop -e
winget install --id Git.Git -e
winget install --id Gitleaks.Gitleaks -e
winget install --id Python.Python.3.12 -e
```

`winget` 패키지가 검색되지 않으면 각 도구의 공식 설치 페이지에서 설치합니다.

- Java 17: Eclipse Temurin JDK 17
- Docker Desktop
- Git for Windows
- Gitleaks
- Python 3

Python 설치 후 PowerShell에서 `pre-commit`을 설치합니다.

```powershell
python -m pip install --user pre-commit
```

설치 후 새 터미널을 열어 PATH를 다시 로드합니다.

### 3.2 설치 확인

PowerShell에서 확인합니다.

```powershell
java -version
docker --version
gitleaks version
pre-commit --version
```

`pre-commit` 명령을 찾지 못하면 Python Scripts 경로가 PATH에 포함되어 있는지 확인합니다.

## 4. 프로젝트 초기 설정

```bash
git clone <repo-url>
+cd <repo-directory>
cp .env.example .env
```

Windows PowerShell에서는 다음처럼 실행할 수 있습니다.

```powershell
git clone <repo-url>
cd dondok\backend
copy .env.example .env
```

`.env`에는 로컬 개발용 DB, Redis, JWT, AWS, Firebase 설정을 채웁니다.
실제 운영 secret이나 개인 credential은 커밋하지 않습니다.

## 5. Git hook 설정

macOS 또는 Windows Git Bash에서 실행합니다.

```bash
pre-commit install
pre-commit install --hook-type pre-push
```

현재 hook 동작은 다음과 같습니다.

| 시점       | 실행 항목                                     |
|----------|-------------------------------------------|
| commit 전 | Spotless 포맷 검사, gitleaks staged secret 검사 |
| push 전   | 백엔드 테스트                                   |

## 6. 로컬 검증 명령

### 6.1 macOS / Linux / Windows Git Bash

개발 중 포맷 자동 수정:

```bash
./gradlew spotlessApply
```

테스트:

```bash
./gradlew test --no-daemon
```

PR 전 최종 품질 게이트:

```bash
./gradlew spotlessCheck checkstyleMain checkstyleTest test --no-daemon
```

Secret 검사:

```bash
gitleaks detect --source . --redact --verbose
gitleaks protect --staged --verbose
```

### 6.2 Windows PowerShell

개발 중 포맷 자동 수정:

```powershell
.\gradlew.bat spotlessApply
```

테스트:

```powershell
.\gradlew.bat test --no-daemon
```

PR 전 최종 품질 게이트:

```powershell
.\gradlew.bat spotlessCheck checkstyleMain checkstyleTest test --no-daemon
```

Secret 검사:

```powershell
gitleaks detect --source . --redact --verbose
gitleaks protect --staged --verbose
```

## 7. Docker/Testcontainers 주의사항

통합 테스트는 Testcontainers로 MySQL, Redis 컨테이너를 실행합니다.
테스트 전 Docker Desktop이 켜져 있어야 합니다.

확인 명령:

```bash
docker ps
docker info
```

Windows PowerShell:

```powershell
docker ps
docker info
```

Docker가 꺼져 있거나 Docker Desktop 초기화가 끝나기 전에 테스트를 실행하면
통합 테스트가 실패할 수 있습니다.

## 8. CI에서 검사하는 항목

GitHub Actions CI는 Docker 이미지 빌드는 하지 않고, 다음 품질 게이트를 실행합니다.

| 영역        | 도구                                      |
|-----------|-----------------------------------------|
| 코드 포맷     | Spotless + google-java-format           |
| 네이밍/정적 규칙 | Checkstyle                              |
| 아키텍처 규칙   | ArchUnit                                |
| 테스트       | JUnit, Spring Boot Test, Testcontainers |
| Secret 검사 | gitleaks                                |
| 취약 의존성    | OWASP Dependency Check                  |

별도 자동화:
- Dependabot (의존성 업데이트 PR 생성)

Repository secret에 `NVD_API_KEY`를 등록하면 OWASP Dependency Check가 더 안정적으로 동작합니다.

## 9. ArchUnit 아키텍처 규칙

ArchUnit은 테스트 단계에서 프로젝트 아키텍처 컨벤션을 자동으로 검증합니다.

- 설정 위치: `build.gradle`
- 규칙 테스트 위치: `src/test/java/com/oit/dondok/architecture/ArchitectureRulesTest.java`
- 실행 시점: `./gradlew test`, CI quality job

### 9.1 현재 강제하는 규칙

| 규칙                                                                                         | 이유                                                                      |
|--------------------------------------------------------------------------------------------|-------------------------------------------------------------------------|
| Controller는 Repository에 직접 의존하지 않는다.                                                       | 웹 계층이 DB 접근 계층을 우회하지 않도록 합니다.                                           |
| Service는 Controller / Web 계층에 의존하지 않는다.                                                   | 비즈니스 계층이 웹 계층에 묶이지 않도록 합니다.                                             |
| Repository는 Service에 의존하지 않는다.                                                             | 데이터 접근 계층이 비즈니스 계층을 역참조하지 않도록 합니다.                                      |
| `@Entity` 클래스는 `domain/{도메인}/entity` 패키지 아래에 둔다.                                          | 도메인 모델의 위치를 일관되게 유지합니다.                                                 |
| `domain` 패키지는 Controller, Config, Security, Web, Request/Response DTO 계층에 의존하지 않는다.         | 도메인이 웹/설정/보안/전달 객체 구현 세부사항에 오염되지 않도록 합니다.                                |
| Spring Component는 계층별 패키지와 접미사(`Controller`, `Service`, `Repository`)를 따른다.               | 컴포넌트 역할과 위치를 일관되게 드러냅니다.                                                |
| `domain/{도메인}/dto/request`와 `domain/{도메인}/dto/response`의 최상위 DTO는 각각 `Request`, `Response`로 끝난다. | 도메인 API DTO 이름을 일관되게 유지하되, Lombok 생성 내부 클래스나 global/common DTO 오탐은 피합니다. |
| Controller는 Entity를 직접 의존하지 않는다.                                                           | Entity를 Response Body로 직접 노출하지 않도록 합니다.                                    |
| Controller mapping path는 path variable을 제외하고 lowercase를 사용한다.                              | API 경로 표기를 일관되게 유지합니다.                                                   |
| Lombok `@Data`는 사용하지 않는다.                                                                  | 의도하지 않은 setter/equality 생성을 방지합니다.                                       |
| Entity는 Lombok `@Builder`를 사용하지 않고 public 생성자를 열지 않는다.                                 | Entity 생성 흐름을 정적 팩토리와 JPA 기본 생성자 패턴으로 제한합니다.                              |
| Entity의 public instance method는 accessor만 허용한다.                                             | 현재 컨벤션의 “Entity에 비즈니스 로직 작성 금지”를 강제합니다.                                |
| 명시적으로 선언한 `@Table`, `@Column` 이름은 `snake_case`를 사용한다.                                    | DB 매핑 이름 표기를 일관되게 유지합니다.                                                |
| Service의 조회성 메서드는 `@Transactional(readOnly = true)`를 선언한다. 단, `getOrCreate`, `findAndUpdate`처럼 쓰기 의도가 드러나는 이름은 제외한다. | 읽기 전용 트랜잭션 컨벤션을 지키되 이름 기반 오탐을 줄입니다.                                    |
| 금액성 이름의 멤버에는 `double` / `float`을 사용하지 않는다.                                             | 금액 계산에서 부동소수점 오차를 방지합니다.                                               |

### 9.2 지켜야 하는 의존 방향

권장 방향:

```text
Controller → Service → Repository
Service → Domain
Repository → Domain
```

금지 방향:

```text
Controller → Repository
Service → Controller
Repository → Service
Domain → Controller / Config / Security
```

### 9.3 권장 패키지 구조

기능 단위 패키지 안에서 계층을 나누는 방식을 기본으로 합니다.

```text
com.oit.dondok
 ├── domain
 │   └── user
 │       ├── controller
 │       ├── service
 │       ├── repository
 │       ├── entity
 │       └── dto
 │           ├── request
 │           └── response
 ├── config
 ├── security
 └── common
```

예시:

```text
UserController  → com.oit.dondok.domain.user.controller
UserService     → com.oit.dondok.domain.user.service
UserRepository  → com.oit.dondok.domain.user.repository
User            → com.oit.dondok.domain.user.entity
UserRequest     → com.oit.dondok.domain.user.dto.request
UserResponse    → com.oit.dondok.domain.user.dto.response
```

### 9.4 위반 예시

Controller에서 Repository를 직접 주입하면 테스트가 실패합니다.

```java
class UserController {

  private final UserRepository userRepository;
}
```

대신 Controller는 Service를 호출해야 합니다.

```java
class UserController {

  private final UserService userService;
}
```

JPA Entity는 `domain` 패키지 아래에 둡니다.

```text
com.oit.dondok.user.domain.User
```

다음처럼 `domain`이 없는 패키지에 Entity를 두면 규칙 위반입니다.

```text
com.oit.dondok.user.entity.User
```

## 10. DTO / Entity 경계 컨벤션

DTO와 Entity는 서로 다른 책임을 가집니다.
Entity는 DB 영속성과 도메인 상태를 표현하고, Request/Response DTO는 API 입출력 모델을 표현합니다.

### 10.1 기본 원칙

- Controller는 Entity를 직접 응답으로 반환하지 않습니다.
- Request DTO를 Entity 생성자나 정적 팩토리에 그대로 넘기지 않습니다.
- Domain/Entity는 Request/Response DTO를 의존하지 않습니다.
- DTO 변환은 Service 또는 별도 Mapper에서 처리합니다.
- API 응답에 필요한 필드만 Response DTO에 명시합니다.

### 10.2 Controller 응답 규칙

금지 예시:

```java
@GetMapping("/{userId}")
public User getUser(@PathVariable Long userId) {
  return userService.getUser(userId);
}
```

허용 예시:

```java
@GetMapping("/{userId}")
public UserResponse getUser(@PathVariable Long userId) {
  return userService.getUser(userId);
}
```

Controller는 API 입출력 모델만 다루고, Entity 노출 여부를 신경 쓰지 않도록 Service가 Response DTO를 반환하는 방식을 우선합니다.

### 10.3 DTO → Entity 변환 규칙

금지 예시:

```java
@Entity
class User {

  public User(UserCreateRequest request) {
    this.email = request.email();
  }
}
```

허용 방향:

```text
Request DTO → Service → Entity 생성
Entity → Service 또는 Mapper → Response DTO
```

예시:

```java
User user = User.create(request.email(), encodedPassword);
return UserResponse.from(user);
```

Entity는 DTO 타입을 몰라야 합니다.
DTO 구조가 바뀌어도 도메인 모델이 함께 흔들리지 않게 하기 위함입니다.

### 10.4 권장 패키지 구조

DTO가 많아지면 request/response를 분리합니다.

```text
user
 ├── controller
 ├── service
 ├── repository
 ├── domain
 └── dto
     ├── request
     └── response
```

아직 규모가 작으면 `dto` 아래에 함께 둘 수 있습니다.

```text
user
 ├── domain
 └── dto
```

### 10.5 추후 자동화 가능 규칙

기능 코드가 늘어나면 다음 규칙은 ArchUnit으로 자동화할 수 있습니다.

- Controller 메서드는 Entity를 직접 반환하지 않는다.
- `domain` 패키지는 `dto` 패키지를 의존하지 않는다.
- Entity 생성자/팩토리는 Request DTO를 파라미터로 받지 않는다.

## 11. 테스트 애노테이션 사용 기준

테스트 설정을 매번 직접 조합하지 않도록, 반복되는 통합 테스트 설정은 커스텀 애노테이션으로 제공합니다.

### 11.1 현재 제공하는 애노테이션

| 목적                                   | 사용 애노테이션           | 포함 설정                                                                   |
|--------------------------------------|--------------------|-------------------------------------------------------------------------|
| MySQL/Redis/Testcontainers 기반 통합 테스트 | `@IntegrationTest` | `integration` profile, `TestcontainersConfiguration`, `@SpringBootTest` |

`@IntegrationTest`는 다음 설정을 한 번에 적용합니다.

```java
@ActiveProfiles("integration")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
```

따라서 MySQL, Redis 컨테이너가 필요한 통합 테스트는 아래처럼 작성합니다.

```java
@IntegrationTest
class UserIntegrationTest {
}
```

### 11.2 테스트 유형별 선택 기준

| 목적                              | 권장 방식                                   |
|---------------------------------|-----------------------------------------|
| 순수 도메인/유틸 로직 테스트                | `@Test`만 사용                             |
| Spring Context 없이 검증 가능한 서비스 로직 | `@Test`와 mock/fake 사용                   |
| Controller slice 테스트            | `@WebMvcTest` 사용 검토                     |
| Repository/JPA slice 테스트        | `@DataJpaTest` 사용 검토                    |
| 실제 MySQL/Redis 연동 통합 테스트        | `@IntegrationTest` 사용                   |
| 아키텍처 규칙 검증                      | `ArchitectureRulesTest`에 ArchUnit 규칙 추가 |

아직 `@WebLayerTest`, `@RepositoryTest`, `@ServiceTest` 같은 추가 커스텀 애노테이션은 만들지 않습니다.
같은 테스트 설정이 2~3개 이상 반복될 때 추가를 검토합니다.

## 12. PR 전 체크리스트

- [ ] `.env` 또는 credential 파일을 커밋하지 않았다.
- [ ] Docker Desktop을 실행한 상태에서 테스트를 통과했다.
- [ ] 포맷/정적 분석/테스트를 통과했다.
- [ ] gitleaks secret 검사를 통과했다.
- [ ] 새 의존성을 추가했다면 이유와 영향 범위를 PR에 적었다.

최소 확인 명령:

macOS / Linux / Windows Git Bash:

```bash
./gradlew spotlessCheck checkstyleMain checkstyleTest test --no-daemon
gitleaks detect --source . --redact --verbose
```

Windows PowerShell:

```powershell
.\gradlew.bat spotlessCheck checkstyleMain checkstyleTest test --no-daemon
gitleaks detect --source . --redact --verbose
```

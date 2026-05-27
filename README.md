# 🤝 Dondok (돈독) — Backend

> **함께 채우는 성실함의 가치, 지분 기반 습관 형성 플랫폼**  
> Spring Boot 기반 RESTful API 서버

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3.2.x |
| Auth | Spring Security + JWT (Access 30분 / Refresh 7일) |
| ORM | JPA (Hibernate) + QueryDSL |
| Batch | Spring Batch 5.x |
| DB | MySQL 8.0 (AWS RDS) |
| Cache | Redis (JWT 토큰 저장 · 지분율 랭킹 캐싱) |
| Storage | AWS S3 + Presigned URL |
| AI | OpenAI GPT-4.1 mini |
| Infra | AWS EC2 (t3.micro, Ubuntu 24.04) |
| Container | Docker (블루-그린 무중단 배포) |
| Proxy | Nginx (리버스 프록시, SSL) |
| CI/CD | GitHub Actions |
| Monitoring | Prometheus + Grafana + Loki + Promtail |
| API Docs | Swagger (SpringDoc OpenAPI) |
| Test | JUnit5 + AssertJ |

---

## 패키지 구조

```
src/main/java/com/oit/dondok/
│
├── DondokApplication.java
│
├── config/
│   ├── OpenApiConfig.java        # Swagger 설정
│   └── QuerydslConfig.java       # QueryDSL 설정
│
├── domain/                       # 도메인별 레이어드 구조
│   ├── auth/                     # 인증/회원 [김세희 담당]
│   │   ├── controller/
│   │   ├── dto/
│   │   │   ├── request/
│   │   │   └── response/
│   │   ├── entity/
│   │   ├── repository/
│   │   └── service/
│   │
│   ├── crew/                     # 크루/참여 [전성 담당]
│   │   ├── controller/
│   │   ├── dto/
│   │   ├── entity/
│   │   ├── repository/
│   │   └── service/
│   │
│   ├── member/                   # 회원 프로필 [문창현 담당]
│   │   ├── controller/
│   │   ├── dto/
│   │   ├── entity/
│   │   ├── repository/
│   │   └── service/
│   │
│   ├── mission/                  # 미션 인증 [김한비 담당]
│   │   ├── controller/
│   │   ├── dto/
│   │   ├── entity/
│   │   ├── repository/
│   │   └── service/
│   │
│   ├── notification/             # 알림 [김한비 담당]
│   │   ├── controller/
│   │   ├── dto/
│   │   ├── entity/
│   │   ├── repository/
│   │   └── service/
│   │
│   ├── point/                    # 포인트 [서일현 담당]
│   │   ├── controller/
│   │   ├── dto/
│   │   ├── entity/
│   │   ├── repository/
│   │   └── service/
│   │
│   ├── settlement/               # 정산 [서일현 담당]
│   │   ├── controller/
│   │   ├── dto/
│   │   ├── entity/
│   │   ├── repository/
│   │   └── service/
│   │
│   └── batch/                    # Spring Batch [서일현 담당]
│       ├── controller/
│       ├── dto/
│       ├── entity/
│       ├── repository/
│       └── service/
│
├── global/                       # 전역 공통 (예외처리, 응답포맷 등)
└── infra/                        # 외부 인프라 연동 (S3, FCM, OpenAI 등)
```

---

## 로컬 개발 환경

### 요구사항
- Java 17+
- Docker Desktop

### 실행 방법

**macOS / Linux / Windows Git Bash**

```bash
# 1. 환경 변수 설정
cp .env.example .env

# 2. 로컬 인프라 실행 (MySQL, Redis)
docker compose up -d

# 3. 애플리케이션 실행
./gradlew bootRun --args='--spring.profiles.active=local'
```

**Windows PowerShell**

```powershell
# 1. 환경 변수 설정
copy .env.example .env

# 2. 로컬 인프라 실행 (MySQL, Redis)
docker compose up -d

# 3. 애플리케이션 실행
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

> **Git hook 설정 필수** — 첫 커밋 전 반드시 [CONTRIBUTING.md](./CONTRIBUTING.md) 섹션 5를 완료하세요.  
> (pre-commit 훅 미설치 시 Spotless · gitleaks 검사 없이 커밋됩니다.)

### 환경 변수 (.env)

```env
DB_URL=jdbc:mysql://localhost:3306/dondok
DB_USERNAME=root
DB_PASSWORD=password
REDIS_HOST=localhost
REDIS_PORT=6379
JWT_SECRET=...
AWS_ACCESS_KEY=...
AWS_SECRET_KEY=...
AWS_S3_BUCKET=...
OPENAI_API_KEY=...
```

---

## API 문서

로컬 실행 후 아래 URL에서 확인:

```
http://localhost:8080/swagger-ui.html
```

---

## Git 전략

| 브랜치 | 용도 |
|--------|------|
| `main` | 프로덕션 배포 |
| `feat/{이슈번호}-{기능명}` | 기능 개발 |
| `hotfix` | 긴급 버그 수정 |

- PR 머지: 최소 1명 코드 리뷰 승인 후 **Squash Merge**
- 커밋 컨벤션: `feat` / `fix` / `refactor` / `docs` / `test` / `chore`

---

## 팀원 및 담당

| 이름 | 백엔드 담당 |
|------|------------|
| 김세희 | GitHub 레포·브랜치 전략, 로그인/로그아웃/토큰 재발급 API, 크루 상세 조회 API, 이모지 리액션 API, 대시보드 API, FCM 설정 |
| 서일현 | Spring Boot 초기화, ERD 설계·JPA 엔티티, 방장 검증 API, 도딘 충전/잔액 API, 일일·최종 정산 배치, Prometheus |
| 문창현 | Docker Compose, GitHub Actions CI/CD, Spring Security·JWT, 회원가입 API, 프로필 조회·수정 API |
| **김한비** | **Nginx+SSL, Flyway, 입장 신청 승인/거절 API, S3 Presigned URL API, 인증 피드 조회 API, OpenAI 연동, Loki+Promtail** |
| 전성 | 크루 생성·목록·입장신청/철회 API, Exif 파싱·자동 검증, SHA-256 해시, 인증 업로드 API, AWS SES |

---

## 관련 문서

- [API 명세서](./docs/api/)
- [아키텍처](./docs/operations/architecture.md)
- [코드 컨벤션](./docs/convention/code-convention.md)
- [Git 컨벤션](./docs/convention/git-convention.md)
- [ERD](#) _(추후 입력)_

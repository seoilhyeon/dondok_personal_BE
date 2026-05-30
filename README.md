# 🤝 Dondok (돈독) — Backend
> **함께 채우는 성실함의 가치, 지분 기반 습관 형성 플랫폼** <br/>
> Spring Boot 기반 RESTful API 서버

<p align="center">
  <img src="https://github.com/lei-3m/AIBE5/blob/main/img/dondok/%EC%95%B1.png?raw=true" width="180"/>
<br/>
  <strong>Dondok</strong>
<br/>
</p>


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
├── global/                       # 전역 공통 (응답포맷 등). 에러코드/Exception은 각 도메인 폴더에 위치
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

- PR 머지: 최소 1명 코드 리뷰 승인 후 **Merge**
- 커밋 컨벤션: `feat` / `fix` / `refactor` / `docs` / `test` / `chore`

---

## 팀원 및 담당

| 이름 | 백엔드 담당 |
|------|------------|
| 김세희 | GitHub 레포·브랜치 전략, ERD 리뷰·피드백, Flyway 마이그레이션, S3 연동·이미지 파이프라인, 인증 피드 조회 API, 이모지 리액션 API, 대시보드 API |
| 서일현 | Spring Boot 초기화, AWS RDS, Swagger 초기 설정, ERD 전체 설계·JPA 엔티티·QueryDSL, 방장 검증 API, 도딘 충전/잔액 API, 일일·최종 정산 배치, 정산 단위/통합 테스트, Prometheus |
| 문창현 | Docker Compose, GitHub Actions CI/CD, Spring Security·JWT, 회원가입 API, 프로필 조회·수정 API, 인원 미달 크루 자동 폐쇄 배치, 검증 이력 조회 API, Grafana 대시보드 |
| 김한비 | Nginx+SSL, 입장 신청 승인/거절 API, 크루 공지 CRUD API, 크루 해체 API, 미검토 입장 신청 자동 거절 스케줄러, FCM 알림 구현, 크루/미션/정산 관련 알림, AWS SES 연동, Loki+Promtail |
| 전성 | OpenAI GPT-4.1 mini 연동, 크루 생성·목록·상세 조회 API, 크루 입장 신청/철회 API, 인원 미달 크루 자동 폐쇄 배치, CREW 단위/통합 테스트 |

---

## 관련 문서

- [API 명세서](./docs/api/)
- [아키텍처](./docs/operations/architecture.md)
- [코드 컨벤션](./docs/convention/code-convention.md)
- [Git 컨벤션](./docs/convention/git-convention.md)
- [ERD](#) _(추후 입력)_

<img src="https://github.com/lei-3m/AIBE5/blob/main/img/dondok/%EB%8F%88%EB%8F%85%20%EB%A1%9C%EA%B3%A0%20v2.2_%ED%88%AC%EB%AA%85.png?raw=true" width="180"/>

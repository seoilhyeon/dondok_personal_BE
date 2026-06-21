# Project3th Architecture

이 문서는 현재 저장소에 구현된 백엔드, 배포, 모니터링 구조와 프론트엔드 배포 방향을 정리한다.

프론트엔드는 Vercel에 배포하고, 백엔드는 Docker 기반 Spring Boot API 서버로 EC2에서 운영하는 구성을 전제로 한다.

## 전체 구조

```text
User Browser
  -> Vercel Frontend
       -> Backend API Endpoint
            -> Nginx on EC2
                 -> Blue or Green API container
                      -> Spring Boot API server
                           -> MySQL

API container
  -> Spring Boot Actuator / Prometheus metrics
  -> Docker json logs

Monitoring stack on EC2
  -> Prometheus scrapes API metrics
  -> Promtail ships Docker logs
  -> Loki stores logs
  -> Grafana visualizes metrics and logs
```

## 구성 요소

| 영역 | 기술 | 역할 |
| --- | --- | --- |
| Frontend | Vercel | 사용자에게 웹 UI를 제공하고 백엔드 API를 호출 |
| Backend | Spring Boot 3.2, Java 17 | REST API, 헬스체크, DB 연동, 운영 메트릭 제공 |
| Database | MySQL | 애플리케이션 영속 데이터 저장 |
| Reverse Proxy | Nginx | 외부 API 진입점, blue/green 컨테이너 트래픽 전환 |
| Container | Docker, Docker Compose | API 서버와 모니터링 스택 실행 |
| CI/CD | GitHub Actions, Docker Hub, EC2 | 이미지 빌드/푸시와 EC2 배포 자동화 |
| Observability | Actuator, Micrometer, Prometheus, Grafana, Loki, Promtail | 헬스체크, 메트릭 수집, 로그 조회 |
| Load Test | k6 | API 부하 테스트와 모니터링 검증 |

## 프론트엔드 아키텍처

프론트엔드는 Vercel을 적용한다. Vercel은 정적 자산과 프론트엔드 런타임을 제공하고, 브라우저에서 백엔드 API 서버로 HTTP 요청을 보낸다.

프론트엔드 저장소 또는 애플리케이션은 다음 설정을 갖는 것을 권장한다.

| 항목 | 값 |
| --- | --- |
| 배포 플랫폼 | Vercel |
| API Base URL | Vercel 환경 변수로 관리 |
| 운영 API 경로 | `http://<backend-host>:8080/api` 또는 도메인 연결 후 `https://<api-domain>/api` |
| 로컬 API 경로 | `http://localhost:8080/api` |

프론트엔드에서 백엔드 주소를 코드에 직접 고정하지 않고, `NEXT_PUBLIC_API_BASE_URL` 같은 공개 환경 변수로 분리한다. 이렇게 하면 로컬, preview, production 환경마다 API 엔드포인트를 다르게 연결할 수 있다.

```text
Vercel Production
  NEXT_PUBLIC_API_BASE_URL=https://<api-domain>/api

Vercel Preview
  NEXT_PUBLIC_API_BASE_URL=https://<staging-api-domain>/api

Local
  NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api
```

현재 백엔드 Nginx 설정은 `/api/` 요청을 active API 컨테이너로 프록시한다. 따라서 프론트엔드는 모든 백엔드 호출을 `/api` 하위 API로 맞추는 방식이 가장 단순하다.

## 백엔드 애플리케이션

백엔드는 Spring Boot 3.2 기반 API 서버다.

현재 구현된 핵심 설정은 다음과 같다.

| 항목 | 내용 |
| --- | --- |
| Java | 17 toolchain |
| Framework | Spring Boot Web MVC |
| Persistence | Spring Data JPA, MySQL Connector |
| Observability | Spring Boot Actuator, Micrometer Prometheus registry |
| DB query observation | `datasource-proxy` 의존성 기반 |
| API context path | local profile 기준 `/api` |
| Runtime port | container 내부 `8080` |

헬스체크 컨트롤러는 다음 두 매핑을 제공한다.

```text
GET /api/health
GET /health
```

운영 배포와 Nginx 진입점 헬스체크는 `/api/health`를 기준으로 동작한다. local profile처럼 `server.servlet.context-path=/api`가 적용된 환경에서는 `/health` 매핑도 최종 외부 경로가 `/api/health`가 되므로, 환경 차이가 있어도 배포 스크립트의 헬스체크 경로를 유지할 수 있다.

## 데이터베이스

애플리케이션은 MySQL을 사용한다. local profile의 기본 연결값은 환경 변수로 덮어쓸 수 있다.

| 환경 변수 | 기본값 |
| --- | --- |
| `LOCAL_DB_URL` | `jdbc:mysql://localhost:3306/Infra?serverTimezone=Asia/Seoul&connectionCollation=utf8mb4_unicode_ci` |
| `LOCAL_DB_USERNAME` | `root` |
| `LOCAL_DB_PASSWORD` | local 설정 파일의 기본값 |

JPA는 local profile에서 `ddl-auto: update`로 동작한다. 운영 환경에서는 GitHub Actions가 `APPLICATION_PROD` 시크릿을 base64 decode하여 `application-prod.yml`을 생성하고, 컨테이너는 `SPRING_PROFILES_ACTIVE=prod`로 실행된다.

## 인프라 어댑터 프로파일 정책

외부 인프라 실제 구현은 기본적으로 `@Profile("!test")`를 사용해 테스트 슬라이스에서 stub/fake와 상호배타적으로 등록한다. 예: S3, Toss Payments.

FCM은 예외다. 기본 `integration` 프로파일에서는 Firebase credentials와 외부 push 발송을 피하기 위해 `StubNotificationSender`를 사용하고, FCM 배선 자체를 검증하는 테스트에서만 `integration-fcm`을 `integration` 위에 추가로 활성화한다. 이 정책은 `FcmProfilePolicy`에 중앙화되어 있으며, real FCM 컴포넌트는 `(!test & !integration) | integration-fcm`, stub sender는 `(test | integration) & !integration-fcm` 조건을 사용한다. real FCM 컴포넌트는 추가로 `app.firebase.credentials-path`가 설정된 경우에만 등록된다.

## 배포 아키텍처

백엔드는 GitHub Actions에서 Docker 이미지를 빌드하고 Docker Hub에 푸시한 뒤, EC2에서 blue/green 방식으로 교체한다.

```text
push to main
  -> GitHub Actions
  -> create application-prod.yml from secret
  -> Docker buildx build
  -> push Docker Hub tags: latest, github.sha
  -> copy deployment files to EC2
  -> run switch-blue-green.sh
  -> pull new image
  -> start inactive slot
  -> health check
  -> switch Nginx upstream
  -> stop old slot
```

blue/green 슬롯은 다음 포트를 사용한다.

| 대상 | 포트 | 설명 |
| --- | --- | --- |
| Nginx entrypoint | `8080` | 외부 API 진입점 |
| Blue API container | `8081 -> 8080` | blue 슬롯 |
| Green API container | `8082 -> 8080` | green 슬롯 |

배포 스크립트는 현재 실행 중인 blue 컨테이너가 있으면 green을 새 타겟으로 선택하고, 없으면 blue를 새 타겟으로 선택한다. 새 컨테이너가 `/api/health`에 `200`을 반환해야 Nginx 설정을 교체한다.

Nginx 전환 이후에도 `http://127.0.0.1:8080/api/health` 진입점 헬스체크를 수행한다. 실패하면 이전 Nginx 설정으로 롤백하고 새 컨테이너를 중지한다.

## 모니터링 아키텍처

모니터링 스택은 EC2의 같은 Docker network에서 실행된다.

| 구성 요소 | 포트 | 역할 |
| --- | --- | --- |
| Prometheus | `9090` | API 서버의 `/api/actuator/prometheus` scrape |
| Grafana | `3000` | 메트릭 대시보드와 Loki 로그 조회 |
| Loki | `3100` | 로그 저장소 |
| Promtail | 내부 | Docker container log 수집 후 Loki 전송 |

Prometheus는 blue와 green 컨테이너를 모두 scrape 대상으로 둔다.

```text
api-server-blue, green
/api/actuator/prometheus
(port 8080)
```

컨테이너 로그는 Docker `json-file` 드라이버로 남기며, Promtail이 `/var/lib/docker/containers`와 Docker socket을 읽어 Loki로 전송한다. API 컨테이너에는 로그 로테이션을 적용해 Promtail 수집 여부와 관계없이 로그 파일이 계속 커져 디스크를 소진하지 않도록 한다.

```yaml
logging:
  driver: "json-file"
  options:
    max-size: "10m"
    max-file: "3"
```

## 이미지 reEncode outbox 운영 전제

미션 이미지 reEncode(원본 EXIF 제거)는 `image_reencode_task` outbox에 적재되어, 커밋 직후 fast-path(전용 executor)와 `@Scheduled` 재처리 배치로 처리된다.

**현재 image reEncode outbox는 단일 worker 전제로 운영한다.** fast-path executor는 `core=max=1`로 제한한다. 결과 기록(finalize)이 행 잠금/조건부 UPDATE 없이 `attempt_version` read-check-write 구조라, worker가 병렬화되면 lease 만료 후 reclaim된 작업에서 stale finalize race 여지가 남기 때문이다.

이 완화 판단은 **단일 app instance 또는 reEncode scheduler가 한 인스턴스에서만 도는** 운영 전제에서만 유효하다. 다중 인스턴스에서 각 인스턴스의 scheduler/fast-path가 모두 돌면 multi-worker 구조가 된다.

- claim 단계(`FOR UPDATE SKIP LOCKED` + `next_attempt_at` lease)는 인스턴스 간 **중복 선점**은 막는다.
- 다만 blue/green 배포의 전환 구간처럼 **두 인스턴스가 잠시 겹치는** 동안 reclaim과 finalize가 교차하면 race 여지가 있다(`attempt_version` fencing이 대부분 차단하나, finalize가 원자적이지 않아 잔여 위험이 남는다).

**worker 병렬화 또는 다중 인스턴스 scheduler 상시 운영 전에는, result finalize를 DB conditional update로 원자화해야 한다.**

```sql
-- 예: complete
UPDATE image_reencode_task
   SET status = 'DONE', last_error = NULL
 WHERE id = :id AND attempt_version = :version AND status = 'PENDING';
```

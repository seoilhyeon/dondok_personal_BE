# Backend Deployment Architecture

이 문서는 Dondok 백엔드 API 서버의 배포 구조와 운영 정책을 정의한다.

백엔드는 Spring Boot 기반 API 서버이며, Docker 이미지로 패키징한 뒤 Backend EC2에서 Blue/Green 방식으로 배포한다. 데이터베이스는 AWS RDS MySQL을 사용하고, 파일과 객체 저장소는 AWS S3를 사용한다. Redis는 현재 prod 필수 의존성이 아니며 향후 Redis-backed 기능을 도입할 때 별도 운영한다.

운영 설정과 시크릿은 Docker 이미지에 포함하지 않는다. GitHub Secrets의 `ENV_PROD` 값을 배포 시점에 Backend EC2의 `/opt/dondok/.env`로 생성하고, 컨테이너 실행 시 `env_file` 또는 `--env-file`로 주입한다.

이 문서의 범위는 백엔드 애플리케이션 빌드, Docker 이미지 생성, GitHub Actions CD, EC2 Blue/Green 배포, Nginx 트래픽 전환, 헬스체크, 롤백, 시크릿 관리, 로그/모니터링 연동까지다. 프론트엔드 Vercel 배포, RDS 생성, S3 버킷 생성, 도메인/DNS 설정은 별도 문서에서 다룬다. Redis는 향후 기능에서 필요해질 때 별도 인프라 문서로 다룬다.


## Personal Lightsail 배포 delta (`backend-personal` 전용)

`backend-personal`의 개인 부하테스트 배포는 이 섹션을 우선 적용한다. 아래 표의 값이 본문 EC2 baseline과 충돌하면 `backend-personal`에서는 이 섹션의 Lightsail 값을 따른다. 나머지 절차는 같은 GitHub Actions + SSH + Docker + Nginx Blue/Green 흐름을 재사용한다. Lightsail Container Service 전환은 이번 범위가 아니다.

개인 Lightsail 배포에서 EC2 baseline을 대체하는 값은 다음과 같다.

| 항목 | 값 |
| --- | --- |
| Deploy target | AWS Lightsail instance |
| App root | `/opt/dondok-personal` |
| Docker image | `<DOCKERHUB_USERNAME>/dondok-personal-backend:<commit-sha>` |
| CD concurrency | `backend-personal-deploy` |
| SSH secrets | `LIGHTSAIL_HOST`, `LIGHTSAIL_USERNAME`, `LIGHTSAIL_SSH_KEY` |

필수 GitHub Secrets는 다음과 같다.

```text
DOCKERHUB_USERNAME
DOCKERHUB_TOKEN
LIGHTSAIL_HOST
LIGHTSAIL_USERNAME
LIGHTSAIL_SSH_KEY
ENV_PROD
ENTRYPOINT_HEALTH_URL
```

선택 GitHub Secrets는 다음과 같다.

```text
SMOKE_TEST_URL
MONITORING_ENV
```

Lightsail 인스턴스는 최초 1회 다음을 준비한다.

```text
Static IP 연결
Docker Engine 설치
Docker Compose plugin 설치
Nginx 설치
배포 유저 docker group 권한 설정 후 재접속
nginx -t / nginx reload용 NOPASSWD sudo 설정
```

Lightsail 방화벽은 외부 진입점만 열고 앱/모니터링 내부 포트는 닫는다.

| 포트 | 정책 |
| --- | --- |
| `22` | 초기 배포용 SSH 허용, 가능하면 관리자 IP로 제한 |
| `80`, `443` | HTTP/HTTPS API 진입점 허용 |
| `8080`, `8081`, `8082` | 외부 차단, 컨테이너는 loopback bind 유지 |
| `3000`, `9090`, `3100` | 외부 차단 권장 |

S3 접근은 인스턴스 IAM Role이 없으면 `ENV_PROD`의 `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` fallback을 사용한다. 이 경우 키는 필요한 S3/SES 권한만 가진 least-privilege IAM user로 발급한다.

원격 스크립트 실행 시 `APP_ROOT`를 함께 전달해야 한다. `start-monitoring.sh`와 `switch-blue-green.sh`는 기본값이 `/opt/dondok`이므로, CD workflow에서 `APP_ROOT=/opt/dondok-personal`을 넘기지 않으면 업로드 경로와 실행 경로가 갈라진다.

## 1. 배포 목표

백엔드 배포의 목표는 `main` 브랜치에 반영된 검증된 코드를 운영 EC2 환경에 자동 배포하는 것이다.

| 목표 | 정책 |
| --- | --- |
| 자동화 | `main` merge 이후 GitHub Actions가 배포를 수행한다. |
| 안정성 | `main` 브랜치 기준 CI가 성공한 경우에만 CD를 실행한다. |
| 무중단에 가까운 배포 | Blue/Green 방식으로 새 컨테이너를 먼저 실행하고 검증한 뒤 Nginx 트래픽을 전환한다. |
| 설정 분리 | 운영 설정은 Docker 이미지에 넣지 않고 GitHub Secrets의 `ENV_PROD`로 관리한다. |
| 외부 인프라 분리 | RDS와 S3는 API 서버 외부 인프라로 둔다. Redis는 현재 prod 필수 의존성이 아니다. |
| 복구 가능성 | 새 컨테이너 검증 실패 또는 Nginx 전환 실패 시 기존 컨테이너를 유지한다. |
| 추적성 | Docker 이미지는 `latest`와 commit SHA 태그를 함께 사용하되, 실제 배포와 롤백 기준은 SHA로 한다. |
| 관측 가능성 | 배포된 애플리케이션은 헬스체크, 메트릭, 로그 수집 대상이 되어야 한다. |

전체 배포 흐름은 다음과 같다.

```text
PR 생성 또는 수정
  -> CI 실행
  -> 테스트, 정적 분석, 보안 검증

main merge
  -> main 브랜치 기준 CI 재실행
  -> CI 성공
  -> Backend CD 실행
  -> Docker image build
  -> Docker Hub push
  -> GitHub Secrets의 ENV_PROD를 EC2에 전달
  -> EC2 /opt/dondok/.env 생성
  -> chmod 600 /opt/dondok/.env
  -> application-prod.yml을 EC2 /opt/dondok/config/로 복사
  -> inactive slot 결정
  -> 새 컨테이너 실행
  -> readiness check
       -> Spring Boot 부팅 확인
       -> RDS MySQL 연결 확인
       -> S3 접근 권한 확인
  -> Nginx switch
  -> Nginx entrypoint health check
  -> S3 upload/download smoke test
  -> 기존 컨테이너 종료
  -> deployed-sha 기록
```

PR에서 CI 실패 시 merge를 막으려면 GitHub branch protection rule을 설정해야 한다. `main` 브랜치에는 `Require status checks to pass before merging`을 활성화하고 CI의 필수 job을 required check로 지정한다.

## 2. 배포 대상

배포 대상은 Spring Boot 기반 백엔드 API 서버다.

| 항목 | 내용 |
| --- | --- |
| Application | Dondok Backend API |
| Runtime | Docker container |
| Deploy target | Backend EC2 |
| Reverse proxy | Nginx |
| Database | AWS RDS MySQL |
| Redis | 현재 prod 미사용. Redis-backed 기능 도입 시 별도 구성 |
| File/Object storage | AWS S3 |
| API path | `/api/**` |
| Basic health path | `/api/health` |
| Readiness path | `/api/actuator/health/readiness` |
| Metrics path | `/api/actuator/prometheus` |
| Profile | `prod` |

### API Path 정책

모든 백엔드 API는 `/api` prefix를 가진다. 현재 백엔드 컨트롤러는 각 mapping에 `/api` prefix를 직접 명시한다.

운영 환경에서는 `server.servlet.context-path=/api`를 설정하지 않는다. 컨트롤러 mapping에 이미 `/api`가 포함된 상태에서 `server.servlet.context-path=/api`를 추가하면 최종 경로가 `/api/api/**`가 되기 때문이다.

Nginx는 `/api` prefix를 제거하지 않는다. 외부 요청 경로를 그대로 active API container로 프록시한다.

```text
Client
  -> https://<api-domain>/api/**
  -> Nginx
  -> http://127.0.0.1:<active-slot-port>/api/**
```

Actuator는 controller mapping의 영향을 받지 않으므로 운영 경로를 `/api/actuator/**`로 맞추기 위해 별도 base path를 설정한다.

```yaml
management:
  endpoints:
    web:
      base-path: /api/actuator
```

주요 운영 경로는 다음과 같다.

| 목적 | 경로 |
| --- | --- |
| 비즈니스 API 요청 처리 | `/api/**` |
| Nginx entrypoint 확인 및 배포 후 외부 진입점 검증 (외부 의존성 검사 없음) | `/api/health` |
| 배포 전환 기준 — RDS, S3 연결을 포함한 컨테이너 readiness 검증 | `/api/actuator/health/readiness` |
| Prometheus 메트릭 수집 — Grafana 연동 및 운영 지표 확인 | `/api/actuator/prometheus` |

`/api/health`는 Actuator가 자동 제공하는 endpoint가 아니라 배포와 Nginx entrypoint 확인을 위한 lightweight health endpoint로 직접 구현한다. 이 endpoint는 외부 진입점이 애플리케이션 컨테이너까지 정상적으로 연결되는지 빠르게 확인하는 용도로 사용한다.

`/api/health` 응답 정책은 다음과 같다.

| 항목 | 정책 |
| --- | --- |
| Method | `GET` |
| Path | `/api/health` |
| Success status | `200 OK` |
| Response body | `{"status":"UP"}` |
| Response header | `Cache-Control: no-store` |
| 외부 의존성 검증 | 포함하지 않음 |
| 인증 | 불필요 |
| 용도 | Nginx entrypoint health check, 배포 후 외부 진입점 확인 |

`/api/health`는 애플리케이션 프로세스가 요청을 받을 수 있는지 확인하는 용도로만 사용한다. RDS, S3 연결 상태는 이 endpoint에서 확인하지 않는다. 외부 의존성 검증은 `/api/actuator/health/readiness`에서 수행한다.

배포 대상에 포함되는 항목은 다음과 같다.

| 포함 항목 | 설명 |
| --- | --- |
| Spring Boot API 서버 | 실제 비즈니스 API를 제공하는 백엔드 애플리케이션 |
| Docker image | 애플리케이션 실행 단위 |
| Docker Compose 또는 Docker run 설정 | EC2에서 blue/green 컨테이너를 실행하기 위한 설정 |
| Nginx upstream 설정 | blue/green 트래픽 전환 대상 |
| `/opt/dondok/.env` | GitHub Secrets의 `ENV_PROD`로부터 생성되는 운영 환경 변수 파일 |
| `application-prod.yml` | 환경 변수 placeholder를 사용하는 운영 설정 파일 |
| 배포 스크립트 | blue/green 전환, 헬스체크, 롤백 처리 |

배포 대상에서 제외되는 항목은 다음과 같다.

| 제외 항목 | 설명 |
| --- | --- |
| Frontend | Vercel에서 별도로 배포한다. |
| AWS RDS 생성 | RDS 인스턴스, subnet group, parameter group 설정은 별도 인프라 문서에서 관리한다. |
| Redis EC2 생성 | 현재 prod 필수 인프라가 아니다. Redis-backed 기능을 도입할 때 별도 인프라 문서에서 관리한다. |
| S3 버킷 생성 | 버킷 생성, bucket policy, lifecycle 설정은 별도 인프라 문서에서 관리한다. |
| 도메인/DNS 설정 | API 도메인 연결은 인프라 설정 문서에서 다룬다. |

## 3. 인프라 구성

운영 인프라는 Backend EC2, AWS RDS MySQL, AWS S3로 구성한다. Redis는 현재 prod 필수 인프라가 아니다.

```text
Client / Frontend
  -> Nginx on Backend EC2
       -> api-blue or api-green container
            -> AWS RDS MySQL
            -> AWS S3
```

| 구성 요소 | 역할 |
| --- | --- |
| Backend EC2 | Docker API 컨테이너와 Nginx 실행 |
| Nginx | 외부 요청을 active slot으로 전달 |
| api-blue | blue 배포 슬롯 |
| api-green | green 배포 슬롯 |
| AWS RDS MySQL | 영속 데이터 저장 |
| Redis EC2 | 현재 prod 미사용. Redis-backed 기능 도입 시 캐시, 토큰, 세션성 데이터, 임시 데이터 저장 |
| AWS S3 | 이미지, 첨부파일, 정적 업로드 객체 저장 |
| Docker Hub | 배포 이미지 저장 |
| GitHub Actions | CI/CD 자동화 |

### 보안 그룹 정책

Backend EC2 인바운드 정책은 HTTPS를 기본 운영 진입점으로 전제한다.

| 포트 | 허용 대상 | 용도 | 최종 권장 정책 |
| --- | --- | --- | --- |
| `80` | `0.0.0.0/0`, `::/0` | HTTP 요청, HTTPS 리다이렉트, 인증서 발급 | HTTPS 적용 시 허용 |
| `443` | `0.0.0.0/0`, `::/0` | HTTPS API 요청 | 운영 최종 진입점으로 허용 |
| `22` | `0.0.0.0/0` (초기 개발), 추후 Self-hosted runner로 전환 시 제한 | SSH 접속, GitHub Actions CD 배포 | 초기: 전체 허용 / 운영 전환 시: 제한 필수 |
| `8080` | 허용하지 않음 | 내부 Nginx 또는 로컬 점검용 포트 | 외부 차단 |
| `8081` | 허용하지 않음 | blue container host port | 외부 차단 |
| `8082` | 허용하지 않음 | green container host port | 외부 차단 |
| `3000` | 관리자 IP만 허용 또는 차단 | Grafana | 운영에서는 외부 차단 권장 |
| `9090` | 허용하지 않음 | Prometheus | 외부 차단 |
| `3100` | 허용하지 않음 | Loki | 외부 차단 |

#### 단계별 SSH 정책

SSH 접근 정책은 개발 단계와 운영 단계를 구분해 적용한다.

**초기 개발 단계 (현재)**

GitHub-hosted runner로 EC2에 SSH 배포하므로 runner IP가 매번 변동된다. 이 기간에는 `22`번 포트를 전체 허용한다.

```text
22번 포트: 0.0.0.0/0 허용
배포 방식: GitHub-hosted runner -> SSH -> EC2
```

이 정책은 임시 정책이며, 운영 전환 전 반드시 제한해야 한다.

**운영 단계 전환 시 (Self-hosted runner)**

Backend EC2에 Self-hosted runner를 직접 설치한다. runner가 EC2 로컬에서 실행되므로 SSH 없이 배포 명령을 직접 수행한다. `22`번 포트는 본인 관리자 IP만 허용한다.

```text
22번 포트: 본인 IP/32만 허용
배포 방식: Self-hosted runner (EC2 로컬) -> 직접 실행
```

Self-hosted runner는 EC2에서 docker, nginx 명령을 직접 실행하므로 repository는 반드시 private으로 운영한다. public repository에서 Self-hosted runner를 사용하면 외부 PR이 악의적인 workflow를 트리거해 EC2에 직접 영향을 줄 수 있다.

Self-hosted runner 설치 방법은 다음과 같다.

```bash
# EC2에서 runner 디렉터리 생성
mkdir -p /home/ubuntu/actions-runner && cd /home/ubuntu/actions-runner

# GitHub 레포 -> Settings -> Actions -> Runners -> New self-hosted runner
# 안내되는 버전으로 다운로드
curl -o actions-runner-linux-x64.tar.gz -L \
  https://github.com/actions/runner/releases/download/v2.x.x/actions-runner-linux-x64-2.x.x.tar.gz

tar xzf ./actions-runner-linux-x64.tar.gz

# runner 등록 (GitHub에서 발급한 토큰 사용)
./config.sh --url https://github.com/<org>/<repo> --token <TOKEN>

# 서비스로 등록 (EC2 재시작 시 자동 실행)
sudo ./svc.sh install
sudo ./svc.sh start

# 상태 확인
sudo ./svc.sh status

# runner 로그 확인
journalctl -u actions.runner.* -f
```

workflow에서 runner 전환 방법은 다음과 같다.

```yaml
# 초기 개발 단계
jobs:
  deploy:
    runs-on: ubuntu-latest

# 운영 단계 전환 후
jobs:
  deploy:
    runs-on: self-hosted
```

최종 운영 인바운드는 다음을 권장한다.

| 포트 | 초기 개발 단계 | 운영 단계 (Self-hosted runner 전환 후) |
| --- | --- | --- |
| `80` | `0.0.0.0/0`, `::/0` | `0.0.0.0/0`, `::/0` |
| `443` | `0.0.0.0/0`, `::/0` | `0.0.0.0/0`, `::/0` |
| `22` | `0.0.0.0/0` (임시) | 본인 IP/32만 허용 |
| `8080` | 차단 | 차단 |
| `8081` | 차단 | 차단 |
| `8082` | 차단 | 차단 |
| `3000`, `9090`, `3100` | 차단 또는 관리자 IP만 | 차단 또는 관리자 IP만 |

외부 인프라 보안 정책은 다음과 같다.

| 대상 | 인바운드 정책 |
| --- | --- |
| RDS MySQL | Backend EC2 보안 그룹에서 오는 `3306`만 허용 |
| Redis EC2 | 현재 prod 미사용. 도입 시 Backend EC2 보안 그룹에서 오는 `6379`만 허용 |
| S3 | public bucket 금지, 애플리케이션 IAM 권한으로만 접근 |
| Blue/Green host port | 외부 공개 금지, 로컬/Nginx 접근용으로만 사용 |

S3는 보안 그룹으로 제어하는 리소스가 아니므로 IAM Role과 bucket policy를 최소 권한으로 설정한다.

### HTTPS 인증서 정책

Backend EC2의 Nginx가 TLS를 직접 종료하는 것을 기본 정책으로 한다. 인증서는 Let's Encrypt와 Certbot으로 발급하고 자동 갱신한다.

Nginx는 Docker 컨테이너가 아니라 Backend EC2 host에 직접 설치해 운영한다. API 애플리케이션만 Docker container로 blue/green 실행하고, host Nginx가 현재 active slot의 host port로 요청을 전달한다.

| 항목 | 정책 |
| --- | --- |
| TLS 종료 위치 | Backend EC2의 Nginx |
| 인증서 발급 | Let's Encrypt |
| 인증서 관리 도구 | Certbot |
| HTTP 80 | 인증서 발급/갱신 challenge와 HTTPS 리다이렉트에 사용 |
| HTTPS 443 | 실제 API 트래픽 처리 |
| 갱신 방식 | Certbot systemd timer 또는 cron 기반 자동 갱신 |
| 갱신 후 처리 | 인증서 갱신 성공 시 `nginx -t` 후 Nginx reload |

Nginx와 인증서 파일 위치는 다음을 기준으로 한다.

이 경로는 Ubuntu EC2의 Nginx 구성을 기준으로 한다.

| 항목 | 경로 |
| --- | --- |
| Nginx site 설정 | `/etc/nginx/sites-available/dondok-api.conf` |
| Nginx site 활성화 symlink | `/etc/nginx/sites-enabled/dondok-api.conf` |
| Let's Encrypt fullchain | `/etc/letsencrypt/live/<api-domain>/fullchain.pem` |
| Let's Encrypt private key | `/etc/letsencrypt/live/<api-domain>/privkey.pem` |
| Certbot renewal 설정 | `/etc/letsencrypt/renewal/<api-domain>.conf` |
| Certbot deploy hook | `/etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh` |

인증서 갱신 정책은 다음과 같다.

```text
1. Certbot이 주기적으로 인증서 갱신 시도
2. 갱신 성공
3. nginx -t로 설정 검증
4. 검증 성공 시 Nginx reload
5. 검증 실패 시 reload하지 않고 기존 인증서와 설정 유지
```

Certbot deploy hook은 인증서가 실제로 갱신된 경우에만 실행되도록 한다. deploy hook에서는 `nginx -t`를 먼저 수행하고, 성공한 경우에만 Nginx를 reload한다.

운영 체크리스트에는 인증서 만료일, Certbot timer 상태, Nginx reload 성공 여부를 포함한다.

## 4. 런타임 구성

API 컨테이너는 `.env`를 통해 운영 설정을 받는다.

```text
SPRING_PROFILES_ACTIVE=prod
TZ=Asia/Seoul

MYSQL_URL=jdbc:mysql://<rds-endpoint>:3306/<db-name>?serverTimezone=Asia/Seoul&connectionCollation=utf8mb4_unicode_ci
MYSQL_USER=<db-user>
MYSQL_PASSWORD=<db-password>

AWS_REGION=ap-northeast-2
AWS_S3_BUCKET=<bucket-name>
AWS_S3_BASE_PREFIX=
AWS_S3_HEALTHCHECK_KEY=healthcheck/s3-readiness
AWS_S3_HEALTHCHECK_TIMEOUT=3s

JWT_SECRET=<jwt-secret>
CORS_ALLOWED_ORIGINS=<frontend-domain>
COOKIE_SECURE=true
COOKIE_SAME_SITE=None
```

S3 인증은 Backend EC2 IAM Role을 사용하는 것을 원칙으로 한다. `.env`에는 AWS access key와 secret key를 넣지 않는다.

| 방식 | 권장도 | 설명 |
| --- | --- | --- |
| Backend EC2 IAM Role | 권장 | 컨테이너가 AWS SDK 기본 credential chain으로 S3에 접근한다. |
| ENV_PROD에 AWS key 저장 | 비권장 | 키 유출 위험과 교체 부담이 있다. 꼭 필요할 때만 사용한다. |

Backend EC2 IAM Role을 사용할 때는 Docker 컨테이너 내부에서도 EC2 Instance Metadata Service(IMDS)에 접근할 수 있어야 한다. EC2 host에서 IAM Role이 보이더라도 Docker bridge network 안의 컨테이너에서 metadata credential을 가져오지 못하면 S3 readiness가 실패한다.

따라서 Backend EC2의 metadata option은 다음 정책을 따른다.

```text
http-endpoint: enabled
http-tokens: required
http-put-response-hop-limit: 2 이상
```

권장값은 `http-put-response-hop-limit=2`다. 이 값은 컨테이너가 EC2 IAM Role credential을 조회할 수 있게 하기 위한 설정이다. CD의 `validate-runtime.sh`는 host IAM Role 확인 후 임시 curl 컨테이너를 실행해 Docker bridge network 안에서도 IMDS token과 IAM Role credential endpoint에 접근 가능한지 검증한다.

`application-prod.yml`은 실제 시크릿 값을 직접 포함하지 않고 환경 변수 placeholder를 사용한다. 현재 prod 설정의 DB 변수명은 `MYSQL_URL`, `MYSQL_USER`, `MYSQL_PASSWORD`를 기준으로 한다.

```yaml
spring:
  datasource:
    driver-class-name: ${DRIVER_CLASS_NAME:com.mysql.cj.jdbc.Driver}
    url: ${MYSQL_URL}
    username: ${MYSQL_USER}
    password: ${MYSQL_PASSWORD}
  cloud:
    aws:
      region:
        static: ${AWS_REGION:ap-northeast-2}

management:
  health:
    redis:
      enabled: false
  endpoints:
    web:
      base-path: /api/actuator
      exposure:
        include: health,prometheus
  endpoint:
    health:
      probes:
        enabled: true
      show-details: never
      group:
        readiness:
          include: readinessState,db,s3

app:
  aws:
    s3:
      bucket: ${AWS_S3_BUCKET}
      base-prefix: ${AWS_S3_BASE_PREFIX:}
      healthcheck-key: ${AWS_S3_HEALTHCHECK_KEY:healthcheck/s3-readiness}
      healthcheck-timeout: ${AWS_S3_HEALTHCHECK_TIMEOUT:3s}
```

`/api/actuator/health/readiness`를 배포 전환 기준으로 사용하려면 prod 설정에서 health probes와 readiness health group을 활성화해야 한다. readiness group에는 현재 prod 기준 `readinessState`, `db`, `s3`를 포함한다. 여기서 `s3`는 애플리케이션에서 직접 구현한 S3 custom `HealthIndicator`의 indicator 이름을 의미한다. 최종 운영 기준에서는 `/api/health`를 readiness 대체 수단으로 사용하지 않고, actuator readiness endpoint를 배포 전환 기준으로 사용한다.

Actuator readiness endpoint가 모든 외부 의존성을 자동으로 검증한다고 가정하지 않는다. RDS는 `db` health indicator로 검증한다. S3는 이미지와 첨부파일을 다루는 핵심 기능이므로 배포 검증에 반드시 포함한다. Spring Boot 기본 health indicator만으로 S3 접근이 검증되지 않을 수 있으므로, S3 custom `HealthIndicator`를 구현해 readiness group에 포함한다. 또한 실제 사용자 기능 관점의 검증을 위해 S3 upload/download smoke test를 추가로 수행한다.

### S3 custom HealthIndicator 구현 명세

S3 custom `HealthIndicator`는 readiness group에 포함되므로 배포 때마다 호출된다. 구현이 무겁거나 timeout 없이 AWS SDK를 호출하면 readiness 전체가 느려질 수 있으므로 다음 명세를 따른다.

| 항목 | 명세 |
| --- | --- |
| 확인 방식 | `HeadBucket` 또는 고정 smoke object에 대한 `HeadObject` |
| 이유 | `ListBucket`은 bucket 내 객체 수에 따라 응답 시간이 달라질 수 있으므로 사용하지 않는다 |
| SDK 호출 timeout | 1~3초 (AWS SDK `overrideConfiguration`의 `apiCallTimeout` 또는 `apiCallAttemptTimeout`으로 설정) |
| 성공 조건 | bucket 또는 지정 object에 정상 접근 |
| 실패 조건 | timeout 초과 또는 접근 오류 |
| 반환 상태 | 성공 시 `UP`, 실패 시 `DOWN` |
| 상세 정보 노출 | `show-details: never` 정책에 따라 상세 정보를 외부에 노출하지 않는다 |
| indicator 이름 | `s3` (readiness group의 `include: readinessState,db,s3`와 일치해야 한다) |

```java
// 구현 예시 (핵심 구조)
@Component("s3")
public class S3HealthIndicator implements HealthIndicator {

  private final S3Client s3Client;
  private final String bucketName;

  @Override
  public Health health() {
    try {
      s3Client.headBucket(r -> r.bucket(bucketName)
          .overrideConfiguration(c -> c
              .apiCallTimeout(Duration.ofSeconds(3))
              .apiCallAttemptTimeout(Duration.ofSeconds(3))
          ));
      return Health.up().build();
    } catch (Exception e) {
      return Health.down().build();
    }
  }
}
```

S3 설정은 현재 공통 설정과 동일하게 `spring.cloud.aws.region.static`과 `app.aws.s3.bucket`을 기준으로 한다. 현재 운영 정책에서는 애플리케이션이 생성하는 object key가 `mission/...`, `profile/...`, `crew/...` 형태이므로 `AWS_S3_BASE_PREFIX`는 비워둔다.

S3 object key는 도메인별 하위 prefix를 그대로 사용한다.

```text
mission/{crewId}/{crewParticipantId}/{uuid}
profile/{memberUuid}/{uuid}
crew/{memberUuid}/{uuid}
healthcheck/s3-readiness
```

따라서 IAM policy는 `prod/*`가 아니라 실제 생성되는 top-level prefix인 `mission/*`, `profile/*`, `crew/*`, `healthcheck/*`를 허용한다. smoke test를 사용하는 경우에는 `smoke-test/*`도 함께 허용한다.

운영 컨테이너가 정상 상태로 판단되기 위해서는 최소한 다음 조건을 만족해야 한다.

```text
1. Spring Boot application context 로딩 성공
2. 내장 WAS가 container 8080 포트에서 요청 수신
3. RDS MySQL 연결 가능
4. S3 접근 가능
5. 필수 환경 변수 누락 없음
```

## 5. CI/CD 흐름

워크플로는 CI와 CD를 분리한다.

```text
.github/workflows/
  ci.yml
  cd-backend.yml
```

CI는 기존 정책과 동일하게 PR과 main push에서 코드 품질과 보안을 검증한다.

```text
pull_request
  -> format check
  -> static analysis
  -> test
  -> secret scan
  -> dependency check

push main
  -> 같은 CI 재실행
```

Backend CD는 `workflow_run` 이벤트로 실행되며, `main` 브랜치 기준 CI가 성공한 경우에만 실행한다.

```text
main merge
  -> CI 실행
  -> CI success
  -> Backend CD 실행
```

`workflow_run`은 CI가 완료된 뒤 base repository 권한으로 실행되므로 GitHub Secrets에 접근할 수 있다. 따라서 트리거 조건과 job 조건을 모두 엄격하게 제한한다. CD workflow는 `main` 브랜치의 CI 성공에만 반응해야 하며, fork PR이나 다른 브랜치의 CI 성공으로 배포가 실행되면 안 된다.

```yaml
on:
  workflow_run:
    workflows: ["CI"]
    types: [completed]
    branches: [main]

jobs:
  deploy:
    if: >
      github.event.workflow_run.conclusion == 'success' &&
      github.event.workflow_run.head_branch == 'main'
```

`branches: [main]`은 workflow trigger 수준의 1차 제한이고, `jobs.deploy.if`의 `head_branch == 'main'`은 job 실행 수준의 2차 가드다. 두 조건을 함께 둔다.

CD에서 Docker image build, tag, deploy 기준 SHA는 반드시 `github.event.workflow_run.head_sha`를 사용한다. 이는 CI가 검증한 커밋과 실제 배포되는 커밋을 일치시키기 위함이다.

```text
checkout ref = github.event.workflow_run.head_sha
image tag    = github.event.workflow_run.head_sha
deploy tag   = github.event.workflow_run.head_sha
```

Backend CD는 동시에 두 개 이상 실행되지 않도록 GitHub Actions concurrency를 설정한다. main에 여러 PR이 연속으로 merge되더라도 EC2의 active slot, inactive slot, Nginx upstream 상태가 서로 꼬이지 않아야 한다.

```text
concurrency group:
  backend-prod-deploy

policy:
  한 번에 하나의 Backend CD만 실행한다.
  이미 배포가 진행 중이면 후속 배포는 대기시키거나 이전 실행을 취소하는 정책 중 하나를 선택한다.
```

권장 정책은 `cancel-in-progress: false`다. 운영 배포는 중간 취소 시 EC2 상태가 애매해질 수 있으므로, 앞선 배포가 끝난 뒤 다음 배포가 순차적으로 실행되도록 한다.

### 단계별 CD 배포 방식

**초기 개발 단계 (현재)**

GitHub-hosted runner에서 SSH로 EC2에 접속해 배포한다.

```yaml
jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4
        with:
          ref: ${{ github.event.workflow_run.head_sha }}

      - name: Transfer ENV_PROD to EC2
        run: |
          echo "${{ secrets.ENV_PROD }}" | base64 -w 0 > /tmp/env_prod.base64
          scp -i <(echo "${{ secrets.EC2_SSH_KEY }}") -o StrictHostKeyChecking=no \
            /tmp/env_prod.base64 \
            ${{ secrets.EC2_USERNAME }}@${{ secrets.EC2_HOST }}:/tmp/env_prod.base64

      - name: Copy application-prod.yml to EC2
        run: |
          scp -i <(echo "${{ secrets.EC2_SSH_KEY }}") -o StrictHostKeyChecking=no \
            src/main/resources/application-prod.yml \
            ${{ secrets.EC2_USERNAME }}@${{ secrets.EC2_HOST }}:/opt/dondok/config/application-prod.yml

      - name: Deploy to EC2
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.EC2_HOST }}
          username: ${{ secrets.EC2_USERNAME }}
          key: ${{ secrets.EC2_SSH_KEY }}
          script: |
            base64 -d /tmp/env_prod.base64 | tr -d '\r' > /opt/dondok/.env
            chmod 600 /opt/dondok/.env
            cd /opt/dondok
            ./deploy/switch-blue-green.sh
```

이 방식은 `22`번 포트를 전체 허용해야 한다. 임시 정책이며 운영 전환 전 반드시 제한한다.

**운영 단계 전환 시 (Self-hosted runner)**

EC2에 Self-hosted runner를 설치하고 `runs-on: self-hosted`로 전환한다. runner가 EC2 로컬에서 직접 명령을 실행하므로 SSH 접속이 불필요하다.

```yaml
jobs:
  deploy:
    runs-on: self-hosted  # EC2 로컬에서 직접 실행
    steps:
      - name: Checkout
        uses: actions/checkout@v4
        with:
          ref: ${{ github.event.workflow_run.head_sha }}

      - name: Create .env
        run: |
          echo "${{ secrets.ENV_PROD }}" | base64 -d | tr -d '\r' > /opt/dondok/.env
          chmod 600 /opt/dondok/.env

      - name: Copy application-prod.yml
        run: |
          cp src/main/resources/application-prod.yml \
             /opt/dondok/config/application-prod.yml

      - name: Deploy
        run: |
          cd /opt/dondok
          ./deploy/switch-blue-green.sh
```

Backend CD는 다음 순서로 수행한다.

```text
1.  Docker Hub 로그인
2.  Docker image build
3.  Docker image push: latest, workflow_run.head_sha
4.  Backend EC2 SSH 접속 (초기) 또는 로컬 실행 (Self-hosted runner 전환 후)
5.  GitHub Secrets ENV_PROD를 /opt/dondok/.env로 생성
6.  .env 권한을 600으로 제한
7.  필수 환경 변수 검증
8.  application-prod.yml을 EC2 /opt/dondok/config/로 복사
    - 초기 개발 단계: scp로 EC2에 전송
    - Self-hosted runner 전환 후: checkout된 레포에서 로컬 cp로 복사
9.  새 image pull
10. 현재 active upstream 확인 및 inactive slot 결정
    - active-upstream.conf symlink가 가리키는 slot과 실제 실행 중인 컨테이너를 함께 확인한다.
    - active-upstream.conf가 blue를 가리키더라도 api-blue 컨테이너가 실행 중이 아니면 active slot은 없는 것으로 판단한다.
    - active가 blue이면 next = green, active가 green이면 next = blue
    - active slot이 없으면 next = blue (최초 배포 상황)
11. inactive slot의 기존 컨테이너 정리
12. inactive slot 컨테이너 실행
13. readiness check
    - RDS, S3 custom HealthIndicator 포함
    - 재시도 정책은 10. 헬스체크 전략 기준 적용
    - 실패 시: 새 컨테이너 중지, 기존 active 컨테이너 유지, CD 실패 처리
14. Nginx upstream 변경
    - 변경 전 현재 symlink 경로를 BACKUP_UPSTREAM으로 저장
    - active-upstream.conf를 새 slot으로 변경
15. nginx -t
    - 실패 시: active-upstream.conf를 BACKUP_UPSTREAM으로 복구, CD 실패 처리
16. nginx reload
    - 실패 시: 9. Nginx 트래픽 전환의 reload 실패 대응 절차 따름
17. Nginx entrypoint health check
    - 실패 시: active-upstream.conf를 BACKUP_UPSTREAM으로 복구, nginx reload, 새 컨테이너 중지
18. S3 upload/download smoke test
    - 실패 시: active-upstream.conf를 BACKUP_UPSTREAM으로 복구, nginx -t 성공 후 reload, CD 실패 처리
    - 성공 시: 19번 진행
19. 이전 slot 컨테이너 종료
20. deployed-sha 기록
    - 실패 시: 배포 성공으로 처리하되 CD 로그에 WARN 기록, 다음 배포 전 수동 복구
```

S3 검증의 기본 정책은 custom `HealthIndicator`를 구현해 readiness group에 포함하는 것이다. S3는 이미지와 첨부파일을 다루는 핵심 기능이므로 smoke test만으로 검증을 끝내지 않는다.

S3 검증 정책은 다음과 같다.

| 구분 | 정책 |
| --- | --- |
| 기본 정책 | S3 custom `HealthIndicator`를 구현하고 readiness group에 포함한다. |
| 보조 정책 | Nginx 전환 후에도 이전 slot을 종료하기 전에 S3 upload/download smoke test를 추가로 수행한다. |
| 실패 처리 | custom health check가 실패하면 Nginx 전환을 금지한다. smoke test가 실패하면 배포 스크립트가 즉시 이전 upstream으로 자동 복구하고 `nginx -t` 성공 후 Nginx를 reload한다. |

S3 custom `HealthIndicator`는 readiness group에 포함되므로 CD 순서의 14번 readiness check에서 함께 검증된다. 별도의 S3 health check 요청을 추가로 실행하지 않는다. 그래도 배포 후에는 이전 slot을 종료하기 전에 실제 업로드/다운로드/삭제 API smoke test를 수행해 사용자 관점의 S3 기능을 확인한다. smoke test 실패 시에는 이전 slot이 살아 있는 상태에서 `active-upstream.conf`를 이전 slot으로 복구하고, `nginx -t` 검증 성공 후 Nginx를 reload한다.

`SMOKE_TEST_URL`은 단순 health endpoint가 아니라, 호출 시 애플리케이션 내부에서 S3 test object를 업로드하고, 다시 다운로드해 내용 또는 metadata를 확인한 뒤, 마지막에 삭제까지 수행하는 smoke endpoint여야 한다. 배포 스크립트는 이 endpoint의 HTTP 200 응답만 판단하므로, S3 동작 검증 책임은 smoke endpoint 구현에 둔다.

S3 smoke test 객체는 운영 데이터와 구분되는 별도 prefix를 사용한다.

```text
smoke test object key:
  smoke-test/<workflow_run.head_sha>

policy:
  업로드 후 즉시 다운로드 검증
  검증 성공/실패와 관계없이 삭제 시도
  삭제 실패는 경고로 기록하고 배포 성공 여부 판단에는 포함하지 않음
```

S3 smoke test key는 `smoke-test/<workflow_run.head_sha>` 형태로 둔다. smoke test를 사용하는 경우 IAM policy에 `smoke-test/*`를 별도로 허용한다. 삭제 실패를 배포 실패로 보지 않는 이유는 이미 사용자 기능 검증은 끝났고, 테스트 객체 잔류는 서비스 장애보다 정리 작업에 가깝기 때문이다. 단, 같은 prefix에 lifecycle rule을 설정해 오래된 smoke test 객체가 자동 삭제되도록 한다.

### Docker Hub 이미지 retention 정책

Docker image는 배포마다 commit SHA 태그가 누적된다. 태그 수가 많아지면 관리 부담이 생기므로 다음 정책을 적용한다.

```text
latest:
  항상 최신 배포 이미지 1개를 가리킨다.

SHA 태그:
  최근 N개(권장: 10)를 보존하고 오래된 태그는 정리한다.

정리 방식:
  Docker Hub automated cleanup 설정 또는
  CD 파이프라인에 오래된 태그 삭제 job을 추가한다.
  수동 정리는 최후 수단으로만 사용한다.
```

## 6. Docker 이미지 전략

백엔드 Docker 이미지는 Spring Boot 애플리케이션을 실행하기 위한 최소 런타임 단위로 관리한다.

Docker 이미지는 애플리케이션 실행 파일과 런타임만 포함하며, 운영 환경 설정과 시크릿은 포함하지 않는다.

### 이미지 빌드 방식

Docker 이미지는 GitHub Actions의 Backend CD 단계에서 빌드한다. CI는 테스트, 포맷, 정적 분석, 보안 검증만 담당하고 Docker image build/push는 수행하지 않는다.

이미지 빌드는 Dockerfile의 multi-stage build 방식을 사용한다.

```text
build stage
  -> JDK 17 기반 이미지 사용
  -> Gradle wrapper로 bootJar 생성
  -> 소스코드, 테스트 코드, Gradle 파일 사용

runtime stage
  -> JRE 17 기반 이미지 사용
  -> build stage에서 생성된 JAR만 복사
  -> 애플리케이션 실행
```

CI에서 이미 테스트를 수행하므로 CD Docker build에서는 `bootJar -x test`를 사용할 수 있다. 단, CI가 성공한 커밋과 Docker build 대상 커밋은 반드시 같아야 한다.

Docker image에 포함한다.

```text
- Spring Boot executable jar
- Java runtime
- 애플리케이션 실행에 필요한 최소 파일
```

Docker image에 포함하지 않는다.

```text
- .env
- application-prod.yml의 실제 시크릿 값
- DB password
- Redis password
- AWS access key
- AWS secret key
- JWT secret
- OAuth secret
- 외부 API key
- GitHub Secrets 값
- EC2 SSH key
- 소스코드와 테스트 코드
- Gradle cache
```

### Buildx 캐시 전략

GitHub Actions runner는 매번 새로운 환경에서 실행되므로 로컬 Docker layer cache를 그대로 사용할 수 없다. 따라서 Docker 공식 `docker/build-push-action`과 Buildx를 사용하고, GitHub Actions cache backend를 사용한다.

**초기 개발 단계 (GitHub-hosted runner)**

```text
cache-from: type=gha
cache-to: type=gha,mode=max
```

**운영 단계 전환 시 (Self-hosted runner)**

EC2 디스크에 Docker layer cache가 그대로 유지되므로 `type=gha` 설정이 불필요하다. `cd-backend.yml`에서 `cache-from`, `cache-to` 설정을 제거하거나 주석 처리한다. 그대로 두면 GitHub Cache에 불필요한 업로드가 발생한다.

```yaml
# Self-hosted runner 전환 후 제거 또는 주석 처리
# cache-from: type=gha
# cache-to: type=gha,mode=max
```

Self-hosted runner에서 Docker build를 수행하면 빌드 중간 레이어와 이전 이미지가 EC2 디스크에 누적된다. 주기적으로 정리하지 않으면 디스크가 가득 찰 수 있으므로 cron으로 자동 정리를 설정한다.

```bash
# /etc/cron.d/docker-prune 또는 crontab -e
# 매주 일요일 새벽 3시에 미사용 이미지/캐시 정리
0 3 * * 0 docker system prune -f >> /var/log/docker-prune.log 2>&1
```

현재 실행 중인 컨테이너가 사용하는 이미지는 prune 대상에서 제외된다. 단, 실행 중이지 않은 이전 배포 이미지(SHA 태그)도 삭제될 수 있으므로 롤백이 필요한 경우 Docker Hub의 SHA 태그를 기준으로 다시 pull한다.

Dockerfile은 캐시 효율을 고려해 의존성 관련 파일을 먼저 복사하고, 소스코드는 나중에 복사하는 구조를 권장한다.

```text
1. Gradle wrapper, settings.gradle, build.gradle 복사
2. 의존성 다운로드 또는 빌드 준비
3. src 복사
4. bootJar 실행
```

`.dockerignore`에는 불필요한 build context가 포함되지 않도록 다음 항목을 추가한다.

```text
.git
.gradle
build
out
.env
.env.*
docs
.github
*.log
```

단, `gradlew`, `gradle/wrapper/**`, `build.gradle`, `settings.gradle`, `src/**`는 Docker build에 필요하므로 제외하면 안 된다.

### 태그 정책

Docker image는 항상 두 개의 태그를 함께 push한다.

```text
<DOCKERHUB_USERNAME>/dondok-backend:latest
<DOCKERHUB_USERNAME>/dondok-backend:<workflow_run.head_sha>
```

| 태그 | 용도 |
| --- | --- |
| `latest` | 사람이 최신 이미지를 확인하기 위한 보조 태그 |
| `<workflow_run.head_sha>` | 실제 배포, 추적, 롤백 기준 태그 |

배포 스크립트는 `latest`가 아니라 반드시 `<workflow_run.head_sha>` 태그를 기준으로 이미지를 pull하고 컨테이너를 실행한다.

## 7. EC2 디렉터리 구조

EC2 배포 루트 디렉터리는 `/opt/dondok`으로 고정한다.

```text
/opt/dondok/
  .env
  config/
    application-prod.yml
  deploy/
    switch-blue-green.sh
    health-check.sh
    validate-env.sh
  nginx/
    active-upstream.conf
    blue-upstream.conf
    green-upstream.conf
  releases/
    deployed-sha.txt
    previous-sha.txt

/home/ubuntu/actions-runner/   <- Self-hosted runner 설치 경로 (운영 단계 전환 시)
```

권한 정책은 다음과 같다.

```text
.env 권한: 600
deploy script 권한: 700
releases: 현재/이전 SHA 기록용
```

배포 스크립트는 운영 설정과 Nginx, Docker를 다루므로 소유자만 읽고 실행할 수 있도록 `700` 권한을 사용한다.

`/opt/dondok/config/application-prod.yml`은 컨테이너 내부의 `/app/config/application-prod.yml`로 read-only mount한다. Spring Boot는 실행 디렉터리 하위의 `config/` 디렉터리를 기본 설정 탐색 경로로 사용하므로, 컨테이너의 working directory를 `/app`으로 고정하면 `/app/config/application-prod.yml`을 자동으로 읽을 수 있다.

```text
host:
  /opt/dondok/config/application-prod.yml

container:
  /app/config/application-prod.yml

mount policy:
  read-only

container working directory:
  /app
```

컨테이너 실행 방식에 따라 working directory가 `/app`이 아닐 수 있다면 `SPRING_CONFIG_ADDITIONAL_LOCATION=file:/app/config/`를 명시해 설정 파일 탐색 경로를 고정한다. 최종 정책은 둘 중 하나가 아니라 다음 조합을 사용한다.

```text
1. application-prod.yml을 /app/config/application-prod.yml로 mount한다.
2. 컨테이너 working directory를 /app으로 둔다.
3. SPRING_CONFIG_ADDITIONAL_LOCATION=file:/app/config/를 함께 지정해 설정 파일 위치를 명시적으로 보장한다.
4. .env는 /opt/dondok/.env에서 env_file 또는 --env-file로 주입한다.
```

`SPRING_CONFIG_ADDITIONAL_LOCATION`은 시크릿이 아니지만 컨테이너가 운영 설정 파일을 읽기 위한 필수 런타임 설정이다. 최종 정책은 이 값을 `ENV_PROD`에 포함하지 않고 `docker run`의 고정 `environment` 값으로 관리하는 것이다. 이렇게 하면 GitHub Secrets에는 실제 비밀값과 환경별 런타임 값만 남기고, 컨테이너 실행 구조에 종속된 고정 값은 배포 스크립트에서 관리할 수 있다.

`application-prod.yml`의 정본은 Git repository의 `src/main/resources/application-prod.yml`로 둔다. 이 파일에는 실제 시크릿 값을 넣지 않고 환경 변수 placeholder만 둔다. CD 파이프라인은 배포할 commit의 `application-prod.yml`을 EC2의 `/opt/dondok/config/application-prod.yml`로 매번 복사해 EC2 파일과 레포 파일이 drift되지 않도록 한다.

```text
source of truth:
  repository src/main/resources/application-prod.yml

deploy target:
  /opt/dondok/config/application-prod.yml

policy:
  CD마다 repository의 application-prod.yml을 EC2로 덮어쓴다.
  EC2에서 application-prod.yml을 수동 수정하지 않는다.
  운영 시크릿은 application-prod.yml이 아니라 /opt/dondok/.env에 둔다.
```

`application-prod.yml`은 환경 변수 placeholder만 포함하므로 active 컨테이너가 실행 중일 때 EC2 파일을 덮어써도 일반적인 Spring Boot 런타임 동작에는 영향을 주지 않는다. Spring Boot는 기본적으로 시작 시점에 설정 파일을 읽고, 실행 중 자동 reload하지 않는다. 단, `@RefreshScope`, Spring Cloud Config, config reload 기능을 도입하는 경우에는 active 컨테이너가 파일 변경의 영향을 받을 수 있으므로 이 정책을 재검토한다.

`application-prod.yml`은 환경 변수 placeholder만 포함하므로 Git repository에 커밋해도 시크릿이 직접 노출되지 않는다. 단, DB URL 패턴, Actuator 경로 등 인프라 구조가 간접적으로 노출될 수 있으므로 repository는 private으로 운영하는 것을 권장한다. public repository를 사용하는 경우 이 파일이 노출하는 정보 범위를 사전에 검토한다.

## 8. Blue/Green 배포 전략

포트는 다음과 같이 나눈다.

```text
Nginx entrypoint:
  80  -> HTTPS 리다이렉트와 인증서 발급
  443 -> 실제 API 트래픽

Blue/Green:
  blue container  -> 127.0.0.1:8081 -> container 8080
  green container -> 127.0.0.1:8082 -> container 8080
```

상태 정책은 다음과 같다.

```text
active = blue 이면 next = green
active = green 이면 next = blue
active slot이 없으면 next = blue
둘 다 떠 있으면 Nginx active upstream 기준으로 active 판단
```

초기 active upstream은 blue를 가리키지만, 첫 배포 전에는 api-blue 컨테이너가 아직 없을 수 있다. 따라서 active slot은 symlink만으로 판단하지 않고, symlink가 가리키는 slot의 컨테이너가 실제 실행 중인지까지 함께 확인한다. active-upstream.conf가 blue를 가리키더라도 api-blue 컨테이너가 실행 중이 아니면 active slot은 없는 것으로 판단하고 next slot은 blue로 결정한다.

둘 다 떠 있는 상태는 이전 배포가 비정상 종료되었거나 이전 slot 정리가 실패한 상태일 수 있다. 이 경우 active slot은 Nginx active upstream과 실제 실행 중인 컨테이너 상태를 함께 기준으로 판단하되, inactive slot은 새 컨테이너 실행 전에 항상 정리한다.

```text
active slot:
  Nginx active upstream symlink와 실제 실행 중인 컨테이너를 함께 기준으로 판단

initial deployment:
  active-upstream.conf -> blue-upstream.conf
  api-blue 컨테이너가 없으면 active slot 없음
  next slot = blue

next slot:
  active가 blue이면 green
  active가 green이면 blue
  active slot이 없으면 blue

pre-run cleanup:
  inactive slot 결정 후 실행 전 api-${NEXT_SLOT} 컨테이너를 제거한다.
  제거 대상은 inactive slot 이름과 정확히 일치하는 컨테이너로 제한한다.
```

```bash
docker rm -f api-${NEXT_SLOT} 2>/dev/null || true
```

이 정리 단계는 inactive slot의 좀비 컨테이너 때문에 host port가 충돌하는 것을 막기 위한 필수 단계다. active slot 컨테이너는 절대 이 단계에서 제거하지 않는다.

새 컨테이너가 readiness를 통과하기 전까지는 Nginx를 전환하지 않는다.

## 9. Nginx 트래픽 전환

Nginx는 active upstream 하나만 바라본다.

```text
active-upstream.conf -> blue-upstream.conf
또는
active-upstream.conf -> green-upstream.conf
```

현재 Nginx site 설정은 `location` 블록 안에서 `active-upstream.conf`를 include하는 방식을 사용한다. 따라서 blue/green upstream 파일에는 `upstream { ... }` 블록을 넣지 않고, `proxy_pass` 한 줄만 둔다.

```nginx
# /opt/dondok/nginx/blue-upstream.conf
proxy_pass http://127.0.0.1:8081;
```

```nginx
# /opt/dondok/nginx/green-upstream.conf
proxy_pass http://127.0.0.1:8082;
```

EC2의 Nginx site 설정은 다음처럼 `location` 내부에서 active upstream symlink를 include한다.

```nginx
server {
    listen 80;
    server_name _;

    location / {
        include /opt/dondok/nginx/active-upstream.conf;

        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

최초 EC2 셋업 시에는 `active-upstream.conf` symlink가 존재해야 한다. 첫 배포 전 초기 active upstream은 blue로 둔다.

```bash
ln -sfn /opt/dondok/nginx/blue-upstream.conf \
        /opt/dondok/nginx/active-upstream.conf
```

이 초기 symlink는 Nginx 설정을 유효하게 만들기 위한 기본값이다. 첫 배포 전에는 `api-blue` 컨테이너가 아직 없을 수 있으므로, 배포 스크립트는 이 symlink만 보고 active slot을 blue로 판단하지 않는다. `active-upstream.conf`가 blue를 가리키더라도 `api-blue` 컨테이너가 실행 중이 아니면 active slot은 없는 것으로 보고, 첫 배포 target을 blue로 선택한다.

첫 배포가 실패하면 배포 스크립트는 새로 실행한 `api-blue` 컨테이너를 제거하고 `active-upstream.conf`는 초기 정책대로 blue upstream을 가리키게 둔다. 이 상태는 Nginx 설정 파일을 유효하게 유지하기 위한 상태이며, `api-blue` 컨테이너가 없으므로 외부 요청은 일시적으로 `502 Bad Gateway`를 받을 수 있다. 첫 배포 실패 후에는 실패 원인을 해결한 뒤 CD를 다시 실행해 `api-blue`를 정상 기동시킨다.

`active-upstream.conf`는 blue 또는 green upstream 설정을 가리키는 symlink로 관리하는 것을 권장한다. 전환 전 기존 symlink를 백업하고, `nginx -t` 실패 시 백업 symlink로 복구한다.

symlink 변경 이후 `nginx -t`를 실행하면 새 upstream 설정을 기준으로 검증한다. 따라서 실패 시 복구가 반드시 보장되어야 한다. 배포 스크립트는 명시적인 복구 함수와 `trap`을 사용해 `nginx -t`, `nginx reload`, entrypoint health check, S3 smoke test 실패 시 이전 symlink로 복구할 수 있어야 한다.

```bash
BACKUP_UPSTREAM=$(readlink /opt/dondok/nginx/active-upstream.conf)

ln -sfn /opt/dondok/nginx/${NEXT_SLOT}-upstream.conf \
        /opt/dondok/nginx/active-upstream.conf

if ! nginx -t; then
    ln -sfn "${BACKUP_UPSTREAM}" /opt/dondok/nginx/active-upstream.conf
    exit 1
fi
```

전환 정책은 다음과 같다.

```text
1. 새 컨테이너 readiness 성공
2. BACKUP_UPSTREAM에 현재 symlink 경로 저장
3. active-upstream.conf를 새 slot으로 변경
4. nginx -t 실행
5. nginx 설정 정상일 때만 reload
6. entrypoint health check 성공
7. S3 upload/download smoke test 성공
8. 이전 컨테이너 종료
```

`nginx -t` 실패 시 reload하지 않는다. 이 경우 기존 upstream이 유지되므로 서비스 영향이 없어야 한다.

`nginx reload`는 기존 연결을 끊지 않고 설정만 다시 읽는다. `nginx reload` 실패는 단순 upstream 설정 문제가 아니라 Nginx 워커 프로세스 또는 host 상태 문제일 수 있다. reload가 실패하면 Nginx 워커 프로세스 자체의 문제일 수 있으므로 restart를 시도하며, restart는 기존 연결이 끊길 수 있어 서비스 순단이 발생한다. 이 경우 다음 순서로 대응한다.

```text
1. active-upstream.conf를 BACKUP_UPSTREAM으로 복구
2. nginx -t 실행
3. nginx reload 재시도
4. reload 재시도 실패 시 nginx restart 시도
5. restart도 실패하면 수동 개입과 알림 필요
```

## 10. 헬스체크 전략

헬스체크는 두 단계로 나눈다.

```text
1. Direct readiness check
   -> 새 컨테이너 포트 직접 확인
   -> 예: 127.0.0.1:8081 또는 127.0.0.1:8082

2. Entrypoint health check
   -> Nginx를 통한 최종 진입점 확인
   -> 예: https://<api-domain>/api/health
```

권장 endpoint는 다음과 같다.

```text
기본 health:
  /api/health

배포 전환 기준 readiness:
  /api/actuator/health/readiness

Nginx entrypoint health:
  /api/health
```

Readiness에 포함할 조건은 다음과 같다.

| 항목 | 정책 |
| --- | --- |
| Spring Boot 부팅 | 필수 |
| RDS 연결 | 항상 필수, Actuator `db` health indicator로 검증 |
| Redis 연결 | 현재 prod에서는 사용하지 않는다. 인증, 세션, 토큰, 분산 락, 큐 등 핵심 기능에 도입하면 다시 필수로 승격한다. |
| S3 접근 | 필수, custom `HealthIndicator`를 readiness group에 포함해 검증 |

현재 prod Redis는 필수 기능에 사용하지 않으므로 readiness 필수 조건에서 제외한다. S3는 핵심 기능이므로 warning으로만 처리하지 않는다. 문서나 배포 스크립트에서 "S3 readiness 검증"이라고 표현할 경우에는 반드시 S3 custom health check가 구현되어 있어야 한다. S3 custom `HealthIndicator`의 구체적인 구현 명세(확인 방식, timeout, 반환 상태 등)는 4. 런타임 구성의 S3 custom HealthIndicator 구현 명세를 따른다. S3 upload/download smoke test는 readiness의 대체 수단이 아니라, Nginx 전환 후 이전 slot을 종료하기 전에 수행하는 추가 검증이다.

Readiness 실패 시 Nginx를 전환하지 않는다. 새 컨테이너는 중지하거나 로그 분석용으로 보존하고 기존 active 컨테이너를 유지한다.

Readiness check는 무한 대기하지 않고 명확한 재시도 정책을 가진다.

```text
readiness check:
  interval: 5s  (이전 시도 완료 여부와 관계없이 5초 간격으로 다음 시도 시작)
  max_attempts: 24
  total_wait: 최대 2분 (5s × 24 = 120s)
  timeout_per_request: 3s
  success_condition: HTTP 200
  failure_condition: max_attempts 초과
```

interval은 이전 요청 완료와 관계없이 5초 간격으로 다음 시도를 시작하는 것을 기준으로 한다. timeout_per_request가 interval보다 짧으므로 요청이 겹치지 않고 total_wait이 정확히 120초가 된다.

초기 몇 회의 실패는 애플리케이션 부팅, RDS connection pool 초기화, S3 health check 지연으로 발생할 수 있으므로 즉시 실패로 보지 않는다. 다만 2분 안에 readiness가 성공하지 못하면 해당 컨테이너는 배포 불가 상태로 판단한다.

## 11. 롤백 전략

롤백은 두 종류로 나눈다.

```text
배포 중 실패:
  -> Nginx 전환 전이면 기존 컨테이너 유지
  -> 새 컨테이너 중지/삭제

Nginx 전환 후 실패:
  -> active-upstream을 이전 slot으로 복구
  -> nginx -t
  -> nginx reload
  -> entrypoint health check
  -> 실패 slot 중지 또는 보존 후 분석
```

롤백 기준은 다음과 같다.

```text
새 컨테이너 실행 실패
readiness 실패
RDS 연결 실패
S3 HealthIndicator 실패
S3 smoke test 실패
nginx -t 실패
nginx reload 실패
entrypoint health check 실패
```

수동 복구가 필요한 경우에는 slot만 되돌리지 않는다. 이전 slot 컨테이너는 정상 배포 완료 후 종료되므로, 수동 복구는 이전 commit SHA 이미지를 다시 배포하는 방식으로 수행한다.

이전 SHA는 다음 순서로 확인한다.

```text
1. CD 실행 로그
2. Docker Hub에 push된 commit SHA tag
3. /opt/dondok/releases/previous-sha.txt
4. /opt/dondok/releases/deployed-sha.txt
```

`deployed-sha.txt`와 `previous-sha.txt`는 수동 이전 SHA 재배포를 돕는 보조 기록이다. SHA 기록은 CD의 마지막 단계이므로, Nginx 전환과 smoke test가 이미 성공하고 이전 slot까지 종료된 뒤 기록만 실패한 경우 배포 자체는 성공으로 본다. 이때 CD 로그에는 `WARN`을 남기고, 다음 배포 전 체크리스트에서 SHA 파일을 수동 복구했는지 확인한다.

```text
deployed-sha 기록 실패 정책:
  배포 성공으로 처리
  CD 로그에 WARN 기록
  수동으로 deployed-sha.txt / previous-sha.txt 복구
  다음 배포 전 SHA 기록 상태 확인
```

## 12. 시크릿/환경 변수 관리

GitHub Secrets는 다음과 같이 관리한다.

```text
ENV_PROD
ENTRYPOINT_HEALTH_URL
DOCKERHUB_USERNAME
DOCKERHUB_TOKEN
EC2_HOST
EC2_USERNAME
EC2_SSH_KEY
```

`ENTRYPOINT_HEALTH_URL`은 Nginx 트래픽 전환 후 실제 API entrypoint를 검증하기 위한 필수 값이다. 운영에서는 `https://<api-domain>/api/health` 형식으로 등록한다. HTTPS 인증서, 도메인, Nginx site 설정은 EC2에서 직접 관리하지만, CD는 이 URL을 호출해 외부 진입점 기준 health check를 수행한다.

`EC2_HOST`, `EC2_USERNAME`, `EC2_SSH_KEY`는 초기 개발 단계에서 GitHub-hosted runner가 SSH로 배포할 때 사용한다. Self-hosted runner로 전환하면 이 세 값은 더 이상 CD에서 사용하지 않는다.

`ENV_PROD`에는 런타임 설정을 넣는다.

```text
SPRING_PROFILES_ACTIVE
TZ
MYSQL_URL
MYSQL_USER
MYSQL_PASSWORD
AWS_REGION
AWS_S3_BUCKET
AWS_S3_BASE_PREFIX
AWS_S3_HEALTHCHECK_KEY
AWS_S3_HEALTHCHECK_TIMEOUT
JWT_SECRET
JWT_ISSUER
JWT_ACCESS_TOKEN_EXPIRATION
JWT_REFRESH_TOKEN_EXPIRATION
CORS_ALLOWED_ORIGINS
COOKIE_SECURE
COOKIE_SAME_SITE
```

`SPRING_CONFIG_ADDITIONAL_LOCATION=file:/app/config/`는 `ENV_PROD`가 아니라 컨테이너 실행 명령의 고정 environment로 지정한다.

필수값과 선택값은 구분해서 관리한다.

| 구분 | 환경 변수 | 설명 |
| --- | --- | --- |
| 필수 | `MYSQL_URL`, `MYSQL_USER`, `MYSQL_PASSWORD` | RDS MySQL 접속 정보 |
| 선택 | `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | 현재 prod에서는 사용하지 않는다. Redis-backed 기능을 되살릴 때만 설정한다. |
| 필수 | `AWS_REGION`, `AWS_S3_BUCKET` | S3 접근에 필요한 region과 bucket |
| 선택 | `AWS_S3_BASE_PREFIX` | 현재 운영 정책에서는 비워둔다. 값을 넣으면 `mission/...` 앞에 prefix가 추가되므로 IAM policy도 함께 바꿔야 한다. |
| 선택 | `AWS_S3_HEALTHCHECK_KEY` | S3 readiness가 확인할 객체 key, 기본값 `healthcheck/s3-readiness` |
| 선택 | `AWS_S3_HEALTHCHECK_TIMEOUT` | S3 readiness 요청 timeout, 기본값 `3s` |
| 필수 | `JWT_SECRET` | JWT 서명 secret |
| 선택 | `JWT_ISSUER` | JWT issuer, 기본값 사용 가능 |
| 선택 | `JWT_ACCESS_TOKEN_EXPIRATION` | access token 만료 시간, 기본값 사용 가능 |
| 선택 | `JWT_REFRESH_TOKEN_EXPIRATION` | refresh token 만료 시간, 기본값 사용 가능 |
| 필수 | `CORS_ALLOWED_ORIGINS` | 운영 프론트엔드 origin |
| 선택 | `COOKIE_SECURE` | HTTPS 운영에서는 `true` 권장 |
| 선택 | `COOKIE_SAME_SITE` | 운영 쿠키 SameSite 정책 |
| 필수 | `SPRING_PROFILES_ACTIVE` | 운영에서는 `prod` |
| 선택 | `TZ` | 운영 timezone, `Asia/Seoul` 권장 |

운영 Cookie 정책은 프론트엔드와 API 도메인 관계에 따라 결정한다.

| 상황 | 정책 |
| --- | --- |
| 프론트엔드와 API가 같은 사이트로 취급되는 경우 | `COOKIE_SECURE=true`, `COOKIE_SAME_SITE=Lax` 또는 서비스 요구에 맞게 설정 |
| 프론트엔드와 API가 cross-site cookie를 주고받아야 하는 경우 | `COOKIE_SECURE=true`, `COOKIE_SAME_SITE=None` |
| HTTPS 운영 | `COOKIE_SECURE=true`를 기본값으로 사용 |
| cross-site cookie 필요 | `COOKIE_SAME_SITE=None`과 `COOKIE_SECURE=true`를 함께 사용 |

현재 정책에서는 cookie domain을 별도로 설정하지 않는다. 예를 들어 cross-site cookie가 필요하다면 운영 쿠키 설정은 다음을 권장한다.

```text
COOKIE_SECURE=true
COOKIE_SAME_SITE=None
```

`COOKIE_SAME_SITE=None`은 HTTPS와 함께 사용해야 하므로 `COOKIE_SECURE=false`와 조합하지 않는다.

S3는 가능하면 AWS key를 넣지 말고 EC2 IAM Role로 처리한다.

EC2 IAM Role 권한은 최소 권한으로 제한한다.

```text
s3:GetObject
s3:PutObject
s3:DeleteObject
s3:ListBucket
```

권한 대상은 전체 S3가 아니라 특정 bucket과 실제 사용 prefix로 제한한다. 현재 운영 정책에서는 `AWS_S3_BASE_PREFIX`를 비우므로 `prod/*`가 아니라 애플리케이션이 직접 생성하는 top-level prefix를 허용한다.

```text
allowed object prefix examples:
  mission/**
  profile/**
  crew/**
  healthcheck/**
  smoke-test/**  <- smoke test를 사용하는 경우
```

smoke test를 사용하지 않는다면 `smoke-test/**` 권한은 제외해도 된다.

`ENV_PROD`를 EC2에 생성할 때는 GitHub Actions 로그에 값이 출력되지 않도록 한다. 멀티라인 값과 특수문자 손상을 줄이기 위해 기본 전달 방식은 base64 인코딩/디코딩으로 한다.

배포 방식에 따라 전달 방법이 다르며, 단계별 workflow 예시는 5. CI/CD 흐름의 단계별 CD 배포 방식을 참고한다.

| 단계 | 전달 방식 |
| --- | --- |
| 초기 개발 (GitHub-hosted runner) | base64로 인코딩해 scp로 EC2에 전송 후 SSH로 디코딩 |
| 운영 (Self-hosted runner) | EC2 로컬에서 GitHub Secrets에 직접 접근해 디코딩 |

GitHub Actions runner와 Backend EC2는 모두 Ubuntu/Linux 환경을 전제로 하며, `.env`는 LF 개행을 사용한다. Windows식 CRLF가 섞일 가능성을 줄이기 위해 decode 후 `\r` 문자를 제거한다. 어떤 방식을 쓰더라도 GitHub Actions 로그에 `.env` 원문이 출력되면 안 된다.

## 13. 로그/모니터링 연동

로그는 세 종류로 나눈다.

```text
Application logs -> docker logs -> Promtail -> Loki
Deployment logs  -> GitHub Actions job logs
Nginx logs       -> /var/log/nginx/access.log, error.log
```

모니터링 대상은 다음과 같다.

```text
API metrics       -> /api/actuator/prometheus
RDS 상태          -> 애플리케이션 health 또는 CloudWatch
S3 오류율         -> 애플리케이션 로그/메트릭 또는 CloudWatch
Nginx 요청/오류   -> Nginx logs
```

애플리케이션 메트릭에는 가능하면 다음을 남긴다.

```text
DB connection pool 상태
S3 upload/download 실패 수
HTTP 5xx 수
배포 버전 SHA
```

## 14. 장애 시나리오

대표 장애와 대응은 다음과 같다.

| 장애 | 대응 |
| --- | --- |
| Docker Hub 로그인 실패 | CD 중단 |
| Docker image build 실패 | CD 중단, 기존 운영 영향 없음 |
| Docker image push 실패 | CD 중단, 기존 운영 영향 없음 |
| EC2 SSH 접속 실패 | CD 중단, 기존 운영 유지 (초기 개발 단계에만 해당, Self-hosted runner 전환 후에는 SSH 불필요) |
| `.env` 생성 실패 | CD 중단, 기존 운영 유지 |
| 필수 환경 변수 검증 실패 | CD 중단, 기존 운영 유지 |
| `application-prod.yml` 검증 실패 | CD 중단, 기존 운영 유지 |
| 새 image pull 실패 | CD 중단, 기존 운영 유지 |
| inactive 컨테이너 실행 실패 | 새 컨테이너 제거, 기존 운영 유지 |
| readiness check 실패 | Nginx 전환 금지, 기존 운영 유지 |
| RDS 연결 실패 | Nginx 전환 금지, 새 컨테이너 중지 |
| Redis 연결 실패 | 현재 prod에서는 해당 없음. Redis-backed 기능을 다시 도입하면 readiness 조건으로 복구 |
| S3 HealthIndicator 실패 | Nginx 전환 금지 |
| S3 smoke test 실패 | 배포 스크립트가 이전 upstream으로 자동 복구하고 `nginx -t` 성공 후 Nginx reload |
| 최초 배포 실패 | `active-upstream.conf`는 blue를 유지하지만 `api-blue`가 제거될 수 있으므로 외부 요청은 502 가능, 원인 해결 후 CD 재실행 |
| nginx -t 실패 | reload 금지, 이전 upstream 유지 |
| nginx reload 실패 | 이전 upstream 복구 후 reload 재시도, 필요 시 restart와 수동 개입 |
| entrypoint health check 실패 | 배포 스크립트가 이전 upstream으로 자동 복구하고 새 컨테이너 중지 |
| deployed-sha 기록 실패 | 배포 성공으로 보되 WARN 기록, 다음 배포 전 수동 복구 |

## 15. 운영 체크리스트

배포 전 체크리스트는 다음과 같다.

```text
GitHub Secrets 등록 완료
ENTRYPOINT_HEALTH_URL 등록 완료 (`https://<api-domain>/api/health`)
ENV_PROD 줄바꿈 정상
ENV_PROD base64 전달 방식 확인
ENV_PROD decode 후 CRLF 제거 정책 확인
Docker Hub token 정상
Docker Hub SHA 태그 retention 정책 설정
EC2 SSH 접속 가능 (초기 개발 단계)
Self-hosted runner 설치 및 서비스 등록 (운영 단계 전환 시)
22번 포트 정책 확인 (초기: 전체 허용 / 운영: 관리자 IP만)
CD concurrency 설정
workflow_run main branch trigger와 job-level if 조건 설정
/opt/dondok 디렉터리 존재
Nginx 설정 경로 정상
active-upstream.conf 초기 symlink 생성 (blue 기준)
첫 배포 시 api-blue/api-green 컨테이너가 모두 없으면 next slot이 blue로 결정되는지 확인
Nginx host 직접 설치 확인
TLS 인증서 발급 완료
Certbot 자동 갱신 설정
RDS security group이 Backend EC2 허용
S3 bucket 존재
EC2 IAM Role에 S3 권한 부여
EC2 metadata option http-put-response-hop-limit 2 이상 설정
Docker 컨테이너 내부에서 EC2 IMDS/IAM Role credential 접근 가능 확인
S3 IAM policy에 smoke-test prefix 포함
S3 smoke test prefix와 lifecycle rule 설정
prod health probes 설정
S3 설정 prefix와 application-prod.yml placeholder 일치
S3 custom HealthIndicator 구현 (HeadBucket/HeadObject, timeout 1~3s, indicator 이름 s3)
S3 upload/download smoke test 구현
application-prod.yml CD 복사 정책 설정
application-prod.yml repository 공개 범위 검토
main branch protection rule 설정
```

Self-hosted runner로 전환할 때 추가로 확인할 항목은 다음과 같다.

```text
cd-backend.yml runs-on: self-hosted 변경 확인
cd-backend.yml cache-from/cache-to 설정 제거 확인
22번 포트 본인 IP/32로 제한 확인
EC2_HOST, EC2_USERNAME, EC2_SSH_KEY secrets 비활성화 또는 제거
application-prod.yml 복사 방식 scp -> cp 변경 확인
ENV_PROD 전달 방식 SSH -> 로컬 직접 디코딩 변경 확인
docker system prune cron 설정
/home/ubuntu/actions-runner 서비스 자동 실행 확인 (sudo ./svc.sh status)
```

배포 중 체크리스트는 다음과 같다.

```text
Docker image build 성공
Docker image push 성공
.env 생성 성공
필수 env 검증 성공
application-prod.yml EC2 복사 성공
active upstream 확인 및 inactive slot 결정 성공
inactive slot 기존 컨테이너 정리 성공
새 컨테이너 실행 성공
readiness 성공 (RDS, S3 custom HealthIndicator 포함)
BACKUP_UPSTREAM 저장 성공
Nginx upstream 변경 성공
nginx -t 성공
nginx reload 성공
entrypoint health 성공
S3 upload/download smoke test 성공
S3 smoke test 객체 삭제 시도 완료
이전 slot 컨테이너 종료 성공
deployed-sha 기록 또는 기록 실패 WARN 확인
deployed-sha 기록 실패 시 수동 복구
```

배포 후 체크리스트는 다음과 같다.

```text
현재 active slot 확인
/api/health 정상
/api/actuator/health/readiness 정상
주요 API smoke test
RDS connection 정상
S3 upload/download 정상
Grafana metrics 확인
Loki logs 확인
Nginx error log 확인
TLS 인증서 만료일 확인
Certbot timer 상태 확인
```

## 최종 원칙

```text
1.  CI는 main에 들어갈 수 있는 코드인지 검증한다.
2.  CD는 main에서 CI가 성공한 커밋만 배포한다.
3.  CD의 checkout, image tag, deploy 기준은 workflow_run.head_sha로 통일한다.
4.  Backend CD는 concurrency로 한 번에 하나만 실행한다.
5.  Docker image에는 코드 산출물과 runtime만 담는다.
6.  운영 설정은 GitHub Secrets ENV_PROD에서 EC2 /opt/dondok/.env로 전달한다.
7.  DB 설정은 현재 prod 설정과 동일하게 MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD를 기준으로 한다.
8.  RDS와 S3는 새 컨테이너 readiness에서 검증한다.
9.  S3는 핵심 기능이므로 custom HealthIndicator를 readiness group에 포함하고, Nginx 전환 후에도 이전 slot 종료 전에 smoke test로 실제 upload/download를 추가 검증한다.
10. Nginx 전환은 RDS, S3 custom HealthIndicator를 포함한 readiness 성공 후에만 수행한다.
11. Nginx upstream 전환 전 BACKUP_UPSTREAM을 저장하고, 이후 실패 시 반드시 복구한다.
12. Nginx는 nginx -t 성공 후에만 reload한다.
13. 전환 실패 시 배포 스크립트가 기존 upstream으로 자동 복구한다.
14. 실제 배포 버전은 commit SHA로 추적한다.
15. 초기 개발 단계에서는 GitHub-hosted runner와 SSH 배포를 사용하고, 운영 전환 시 Self-hosted runner로 교체해 22번 포트를 제한한다.
```

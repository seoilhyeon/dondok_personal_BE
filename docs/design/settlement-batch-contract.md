# Settlement Batch Contract Alignment (BATCH-000)

이 문서는 백엔드 정산 배치 구현 전에 확인해야 하는 active contract 정렬 결과다.
BATCH-000은 문서/계약/runbook 정렬 작업이며, 배치 구현·스키마 변경·테스트 코드 추가는 포함하지 않는다.

## 1. 범위

### In scope

- 정산 상태값과 실패 코드의 active contract 고정
- 정산 summary/detail 응답 필드 기준 정리
- FE 타입과 active API 계약의 불일치 기록
- BATCH-005/BATCH-006에서 green-slice로 구현할 테스트 항목 연결

### Out of scope

- Spring Batch job/step 구현
- 정산 계산기 구현
- `Settlement`, `SettlementItem`, `PointHistory` 스키마 변경
- API 응답 필드 추가/삭제 구현
- 실패하는 테스트 또는 disabled placeholder test 추가

## 2. Canonical settlement statuses

| Status | 의미 | 비고 |
| --- | --- | --- |
| `NONE` | `Settlement` row가 아직 없음 | API projection-only, DB 저장 상태 아님 |
| `PENDING` | 정산 row가 생성되어 실행 대기 | `Settlement.status` |
| `RUNNING` | worker/operator가 claim 후 실행 중 | `Settlement.status` |
| `SUCCEEDED` | 모든 `settlement_item`이 검증된 `point_history`와 연결됨 | `Settlement.status` |
| `FAILED` | 재시도 소진 또는 terminal failure | `Settlement.status` |
| `RETRY_WAIT` | 복구 가능한 실패 후 재시도 대기 | `Settlement.status` |

## 3. Canonical failure codes

정산 실패 코드는 아래 목록으로 고정한다.

- `INPUT_LOAD_FAILED`
- `CALCULATION_FAILED`
- `POINT_CREDIT_FAILED`
- `DUPLICATE_SETTLEMENT`
- `LOCK_ACQUIRE_FAILED`
- `UNKNOWN`

## 4. Active response contract

### `GET /api/crews/{crewId}/settlement`

Summary 응답은 방 기준 정산 상태만 제공한다.

Required fields:

- `crew_id`
- `settlement_id`
- `status`
- `retry_count`
- `failure_code`
- `failure_message`
- `started_at`
- `finished_at`

`status = NONE`이면 `settlement_id`, `started_at`, `finished_at`은 `null`일 수 있다.

### `GET /api/settlements/{settlementId}`

Detail 응답은 정산 결과와 참여자별 지급 스냅샷을 제공한다.

Header-level remainder fields:

- `total_remainder_amount`
- `remainder_policy`

Item-level remainder fields:

- `base_refund_amount`
- `remainder_bonus_amount`
- `refund_amount`

`HOST_REMAINDER`는 MVP fixed deterministic policy다. host discretion, random winner, payout mutation authority, ledger authority를 의미하지 않는다.

## 5. Fields that are not active API contract

아래 필드는 BATCH-000 기준 active Settlement API field가 아니다.

- `remainder_winner_*`
- `remainder_winner_crew_participant_id`

잔액 배정 결과는 `remainder_policy`와 각 item의 `remainder_bonus_amount`로만 표현한다.
필드 추가가 필요하면 API/product contract 변경 이슈로 별도 처리한다.

## 6. Recorded contract drift

| ID | Drift | Backend action |
| --- | --- | --- |
| `DRIFT-001` | FE 타입에 `settlement_type`이 있지만 active summary/detail API 문서에는 없음 | 구현하지 말고 FE/API follow-up에서 정렬 |
| `DRIFT-002` | FE 타입에 `remainder_winner_crew_participant_id`가 있지만 active API field가 아님 | 구현하지 말고 FE 타입 follow-up에서 제거/무시 |
| `DRIFT-003` | `backend/docs/api/settlement.md` 일부 설명 prose가 mojibake 상태 | field contract는 유지하고, prose 정리는 docs hygiene follow-up |
| `DRIFT-004` | 정산 상태값과 실패 코드 목록은 active API 계약과 FE enum 기준으로 정렬되어 있음 | 변경 불필요 |
| `DRIFT-005` | `NONE` projection-only, `SUCCEEDED`의 `settlement_item + point_history` linkage 기준은 active API 계약에 이미 명시되어 있음 | 변경 불필요 |

## 7. Backend implementation handoff

### BATCH-005: calculator

- scale 6, `RoundingMode.FLOOR` 기준 `share_ratio` 계산
- all-fail이면 각 participant의 원금 환급
- remainder는 `HOST_REMAINDER` 정책으로 host item의 `remainder_bonus_amount`에만 반영
- `double`/`float` 금지, 금액은 원화 정수 단위

### BATCH-006: API contract

- summary/detail 응답은 snake_case 유지
- 내부 `member.id`는 노출 금지
- detail 응답에서 `remainder_winner_*` 노출 금지
- `SUCCEEDED`는 모든 item의 `point_history_id` 연결 검증 이후에만 반환

### FE-SETTLE-001 연동 유의사항

- FE fixture는 이 문서의 status/failure/remainder contract 기준으로 작성
- FE 화면은 `FAILED`, `RETRY_WAIT`, `PENDING`, `RUNNING`을 사용자 안전 문구로 분리
- FE가 contract에 없는 필드를 요구하면 API 구현 전에 follow-up issue로 정렬

## 8. Verification rule

BATCH-000 완료 조건:

- 문서/계약/runbook만 변경
- source code, schema, test code 변경 없음
- failing test 또는 disabled placeholder test 없음
- 구현이 필요한 mismatch는 follow-up으로 기록

관련 백엔드 API 문서: `../api/settlement.md`
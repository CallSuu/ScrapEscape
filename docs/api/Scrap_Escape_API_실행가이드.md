# Scrap Escape — 백엔드 API 실행 가이드

기준 주소: `http://localhost:8080`
기술: Java 17, Spring Boot 3.5.16, JdbcTemplate, MySQL
모든 요청은 개인 PC에서 실행 중인 서버에 전송한다.

## 1. 서버 준비

IntelliJ의 Project SDK·Gradle JVM을 Java 17로 설정하고 `ScrapEscapeApplication`을 실행한다. MySQL 비밀번호는 프로젝트 루트의 Git 제외 파일 `application-local.properties`에 설정한다. Gradle을 사용하여 터미널에서 실행하는 방법은 README를 참고한다.

## 2. URL과 요청

| 메서드 | URL | JSON 입력 | 정상 상태 |
|---|---|---|---|
| POST | /api/games | 없음 | 201 |
| GET | /api/games/{gameId} | 없음 | 200 |
| GET | /api/games/{gameId}/locations | 없음 | 200 |
| POST | /api/games/{gameId}/explorations | {"locationId":1} | 200 |
| PATCH | /api/games/{gameId}/items/{batchId}/storage | {"storage":"CHEST"} | 200 |
| POST | /api/games/{gameId}/items/{batchId}/clean | 없음 | 200 |
| DELETE | /api/games/{gameId}/items/{batchId} | 없음 | 200 |
| POST | /api/games/{gameId}/repairs/{partId} | 없음 | 200 |
| POST | /api/games/{gameId}/rests | {"hours":6} | 200 |
| POST | /api/games/{gameId}/treatments | 없음 | 200 |
| POST | /api/games/{gameId}/escape | 없음 | 200 |

장소 ID: 1=고물상, 2=전자제품 매장, 3=마트, 4=정비소, 5=통신 시설, 6=병원. 부품 ID: 1=엔진 시동 장치, 2=동력 전달 장치, 3=연료 공급 장치, 4=냉각 장치, 5=비행 제어 장치, 6=통신·항법 장치, 7=기체 보강 장치. 해당 판에 선택된 4개만 수리한다.

## 3. 복사해서 실행하는 순서

새 게임:

```bash
curl -i -X POST http://localhost:8080/api/games
```

응답의 `gameId`를 변수에 넣는다. 아래 문자열은 실제 반환한 ID로 바꾼다.

```bash
GAME_ID='반환된-gameId'
curl "http://localhost:8080/api/games/$GAME_ID"
curl "http://localhost:8080/api/games/$GAME_ID/locations"
```

고물상 탐색:

```bash
curl -X POST "http://localhost:8080/api/games/$GAME_ID/explorations" \
  -H 'Content-Type: application/json' -d '{"locationId":1}'
```

탐색에서 수량이 0일 수 있다. 재료가 있다면 `state.inventory`의 `batchId`를 사용한다.

```bash
BATCH_ID='반환된-batchId'
curl -X PATCH "http://localhost:8080/api/games/$GAME_ID/items/$BATCH_ID/storage" \
  -H 'Content-Type: application/json' -d '{"storage":"CHEST"}'
curl -X POST "http://localhost:8080/api/games/$GAME_ID/items/$BATCH_ID/clean"
```

세척 시 기존 같은 종류의 세척 묶음과 합쳐지면 batchId가 바뀔 수 있다. 이후 행동은 최신 응답의 묶음 ID를 사용한다.

폐기:

```bash
curl -X DELETE "http://localhost:8080/api/games/$GAME_ID/items/$BATCH_ID"
```

휴식:

```bash
curl -X POST "http://localhost:8080/api/games/$GAME_ID/rests" \
  -H 'Content-Type: application/json' -d '{"hours":6}'
```

제작은 `state.repairs`의 `canCraft=true`인 부품을 선택한다.

```bash
PART_ID='제작가능한-partId'
curl -X POST "http://localhost:8080/api/games/$GAME_ID/repairs/$PART_ID"
```

## 4. 응답 형식

게임 생성과 조회는 상태 객체를 반환한다. 상태에는 게임 ID, 날짜·시간, 경과·남은 시간, 체력, 세척액, 보관 목록, 수리 목표, 통계가 포함된다. 장소 조회는 장소 카드 배열이다.

행동 응답:

```json
{
  "message": "행동 결과 메시지",
  "state": {"gameId":"UUID", "status":"RUNNING", "elapsedHours":6},
  "loot": {"quantity":3, "collectedQuantity":3, "leftBehindQuantity":0, "materials":[{"materialId":1,"name":"철 조각","quantity":1,"collectedQuantity":1},{"materialId":2,"name":"기어","quantity":2,"collectedQuantity":2}], "cleanerFound":true, "cleanerAdded":1, "medicalKitsAdded":0}
}
```

위 응답은 주요 필드만 발췌한 형식 예시다. 탐색 이외 행동의 loot는 null이다. 재료 0개일 때 materials는 빈 배열이며 세척액·병원 키트는 별도로 얻는다. quantity는 발견 총수량, collectedQuantity는 실제 보관 수량, leftBehindQuantity는 공간 부족으로 남긴 수량이다. 통계 collected에는 실제 보관분만 누적한다. 각 후보의 weight는 동일한 상대 가중치1이며 확률%가 아니다. 수량이2개 이상이면 서로 다른2종으로 나누고, 한 칸만 남으면 첫 선택 재료만 저장한다. 통이 가득 차면 cleanerFound가 true여도 cleanerAdded는 0이다.

상태 값은 RUNNING, SUCCESS, FAILED이고 재료 상태는 DIRTY, CLEAN, UNUSABLE이다. 보관 위치는 INVENTORY 또는 CHEST다. 미세척 재료의 expiresInHours는 남은 시간, 세척 완료·사용 불가 재료는 null이다.

## 5. 오류

```json
{"code":"INSUFFICIENT_MATERIALS", "message":"세척된 재료가 부족합니다."}
```

| HTTP 상태 | 주요 코드 | 상황 |
|---|---|---|
| 400 | INVALID_REQUEST | 잘못된 UUID, 필수 값 누락, 소수·범위 밖 휴식 시간, 잘못된 보관 위치 |
| 404 | NOT_FOUND | 없는 게임·재료·장소 또는 해당 판의 수리 대상이 아닌 부품 |
| 409 | INVENTORY_FULL / STORAGE_FULL | 슬롯 부족 |
| 409 | INSUFFICIENT_STAMINA / INSUFFICIENT_CLEANER / INSUFFICIENT_MATERIALS / INSUFFICIENT_MEDICAL_KIT | 자원 부족 |
| 409 | NOT_CLEANABLE / SAME_STORAGE / ALREADY_REPAIRED / GAME_ENDED / LEGACY_GAME / REPAIRS_INCOMPLETE | 불가능한 행동 |
| 404 / 405 / 415 | HTTP_ERROR | 없는 URL, 허용되지 않은 HTTP 메서드, 잘못된 Content-Type |
| 500 | INTERNAL_ERROR | 서버·DB 처리 오류, 관련 변경 롤백 |

## 6. DB에서 CRUD 확인

터미널에서 `mysql -u root -p`로 접속한 뒤 실행한다.

```sql
USE scrap_escape;
SHOW TABLES;
SELECT * FROM location;
SELECT * FROM material;
SELECT * FROM recipe ORDER BY part_id, material_id;
SELECT game_id, rules_version, elapsed_hours, stamina, contamination, diseased, medical_kit_amount, status, failure_reason FROM game;
```

화면/API에서 발급된 ID를 아래에 넣어 같은 판만 확인한다.

```sql
SET @game_id = '반환된-gameId';
SELECT batch_id, material_id, quantity, storage_type, acquired_hour, item_condition
FROM item_batch WHERE game_id = @game_id;
SELECT * FROM game_repair WHERE game_id = @game_id;
```

탐색 후 묶음 INSERT, 이동·세척 후 행 UPDATE, 폐기·전량 제작 소비 후 DELETE를 비교한다. 만료는 자동 DELETE가 아니라 UNUSABLE로 UPDATE다. 제작 소비와 수리 완료는 같은 트랜잭션이다.

## 7. 생존·시간·화면 연동

- `state.rulesVersion=2`: 새 규칙 판. 이전 버전 판은 조회만 가능하며 행동 시409 LEGACY_GAME
- `state.stackLimit=6`, `inventoryCapacity=4`, `chestCapacity=16`
- `state.contamination`, `diseased`, `treatmentRemainingHours`, `consecutiveExplorations`, `medicalKitAmount`로 상태 표시
- `currentStorm`·`nextStorm`의 시작·종료는 게임 시작 후 경과 시간. 화면 시각은 시작08:00을 더해 환산
- 장소 카드의 `durationHours`·`staminaCost`·`expectedContaminationIncrease`·`canExplore`·`unavailableReason`로 실행 버튼 제어
- 오염0~59 발병0%,60~79=30%,80~89=40%,90~100=60%. 탐색 종료에 판정하며 이미 질병이면 기한을 초기화하지 않음
- 질병은 탐색시간+2·체력+2. 72시간 미치료 또는 경과240시간이면 실패하며 failureReason은 UNTREATED_DISEASE 또는 TIME_LIMIT
- 한 번에4시간 이상 휴식은 오염−10·연속 횟수초기화,6시간 이상은 체력보너스+2. 휴식으로 질병 치료 불가
- 키트는 최대2,병원확정1. 사용은1시간·체력0·오염−60·질병제거. 발병72시간 전에 치료를 완료해야 하며 기한에 도달하면 실패가 우선
- 치료 직후 발병 판정 없음. 예방 용도로도 사용 가능
- 모든 수리 후 자동 성공하지 않고 `state.escape.allowed=true`가 되며 escape API를 눌러 성공
- `state.treatment`·`escape`는 실행 가능 여부·제한 이유·시간·체력 비용 제공. 실행 중 중복 클릭은 화면에서도 차단
- 미세척 `expiresInHours`로 아이콘 위 “사용 불가까지 N시간” 표시.12시간 이하 주황,6시간 이하 빨강. 이 UI 자체는 아직 미구현

```bash
curl -X POST "http://localhost:8080/api/games/$GAME_ID/explorations" -H 'Content-Type: application/json' -d '{"locationId":6}'
curl -X POST "http://localhost:8080/api/games/$GAME_ID/treatments"
curl -X POST "http://localhost:8080/api/games/$GAME_ID/escape"
```

## 8. DB 변경과 검증

Flyway V1은 초기 스키마·고정 데이터, V2는 관계·자원·위험·확장 조합법, V3는 기존6개 초과 묶음 분할, V4는 DB 수량 제약이다. 기존 판의 조합법과 부품 이름을 game_recipe·game_repair에 보존한다. 기존 판은 삭제하지 않고 조회만 허용한다.

MySQL 통합 테스트는 별도 scrap_escape_test에서 수행하며 fresh/legacy 임시 테스트 DB에서 재실행 시 마이그레이션이 중복 적용되지 않음을 확인한다. 최신 실행 결과는 docs/backend/Scrap_Escape_백엔드_확장_검증.md에 기록한다. UI와 실제 탈출률·플레이 시간 밸런스 검증은 다음 단계다.

## 9. 비밀의 열쇠와 방

| 메서드 | URL | 입력 | 기능 |
|---|---|---|---|
| GET | /api/games/{id}/secret-rooms | 없음 | 발견한 방만 조회 |
| GET | /api/games/{id}/secret-rooms/{locationId} | 없음 | 비용·제한·결과 이력 |
| POST | /api/games/{id}/secret-rooms/{locationId}/open | {"mode":"IMMEDIATE"} 또는 {"mode":"REVISIT"} | 열쇠를 사용해 입장 |
| POST | /api/games/{id}/secret-rooms/{locationId}/defer | 없음 | X·지나치기, 위치 기억 유지 |

기존 게임·장소·재료 API에 호환되는 필드를 추가했다.

- `state.secrets`: keyAmount(0~2),keyCapacity,확률,rooms,statistics
- `state.secrets.statistics`: discoveredRooms/openedRooms/unopenedRooms,keysAcquired/keysUsed,goodOutcomes/badOutcomes,materialsCollected/materialsLost. 남은 열쇠는 secrets.keyAmount
- `locationCard.secretRoom`: 미발견이면 null. 발견한 장소에만 buttonLabel값 `?`를 표시
- `secretRoom.immediate`와 `revisit`: allowed·unavailableReason·durationHours·staminaCost·keyCost·오염·폭풍 노출·rewardFreeSlots·deadlineRisk
- `secretRoom.immediateAvailable`: 즉시 입장 기회. GET은 유지하며 X·다른 유효 행동은 종료. 실패한 요청은 롤백하므로 기회를 소모하지 않음
- `secretRoom.lastVisit`: 한 번 개방한 방의 방식·시간·체력·결과·보상·손실 이력
- 일반 탐색 행동 응답 `secretFindings`: keyFound·keysAdded·roomDiscovered·roomLocationId
- 방 입장 응답 `secretRoom`: 결과·실제 비용·오염 증가·폭풍 노출·loot·lostQuantity

열쇠가 없거나 체력이 부족해도 GET으로 방 정보는 볼 수 있다. `?`는 정보창 버튼이며 열 수 있고, 실제 입장 버튼만 allowed=false일 때 회색 처리한다. 일반 인벤토리가 가득 차도 방 입장 자체는 가능하다. 재료 보상은 저장할 수 없으면 현장에 남기고 leftBehindQuantity로 안내한다.

발견 직후 IMMEDIATE는1시간·체력1이다. X를 눌러 defer했거나 다른 유효한 행동을 했으면 이 모드를 재사용할 수 없다. 기억한 방의 REVISIT는 낮4시간·체력2,밤5시간·체력3이며 질병이면 각각+2다. 시간은 이동·개방을 포함한다. 다른 일반 장소 탐색은 방 개방 여부와 관계없이 계속 가능하다.

IMMEDIATE는 폭풍 노출만 기본 오염에 적용한다. REVISIT는 기본+10,연속3회째 추가+5,폭풍노출당+5이며 연속 탐색 횟수에 포함한다. 방은 일반 탐색 횟수나 병원 확정 키트·일반 재료·세척액·열쇠를 추가 지급하지 않는다. 두 모드 모두 방 결과의 오염+15가 있으면 별도로 더한다.

결과 확률은 재료3~4개50%,세척액2회분 또는 의료 키트1개30%(각각반반),미세척 인벤토리 재료1~2개 손실10%,오염+15가10%다. 손실 대상이 없으면 오염+15로 대체한다. 손실은 입장 시점의 미세척 인벤토리만 대상으로 하고 보관함·세척·사용 불가 재료는 보호한다.

보상 재료는 방 종료 시각의 새 미세척 묶음이며 기존 묶음의 유효 시간을 연장하지 않는다. 보급품은 보유 한도까지만 저장한다. collected 통계에는 실제 저장한 방 재료도 포함하고, 방 손실은 discarded에 포함하지 않고 별도 집계한다.

게임 종료 이후에는 새 열쇠·방 발견과 방 입장을 막는다. 방문 중 질병 기한 또는240시간에 도달하면 키트로 회복하거나 보상으로 취소할 수 없으며 실패한다. deadlineRisk가 true이면 실행 전에 기한 도달을 안내한다.

새 오류:409 INSUFFICIENT_SECRET_KEY / ROOM_ALREADY_OPENED / IMMEDIATE_UNAVAILABLE / INSUFFICIENT_STAMINA. 미발견 방은404 NOT_FOUND, 잘못되거나 누락된 mode는400 INVALID_REQUEST.

```bash
curl "http://localhost:8080/api/games/$GAME_ID/secret-rooms"
curl -X POST "http://localhost:8080/api/games/$GAME_ID/secret-rooms/1/defer"
curl -X POST "http://localhost:8080/api/games/$GAME_ID/secret-rooms/1/open" -H 'Content-Type: application/json' -d '{"mode":"REVISIT"}'
```

V5는 GAME에 열쇠 수량·획득·사용 누계를 추가하고 GAME_SECRET_ROOM에 게임·장소 복합 PK와 방 발견·개방·결과 이력을 저장한다. 방 상태·열쇠·보상·손실·체력·시간·통계는 부모 게임 행 잠금과 같은 트랜잭션으로 처리한다.

## 변경 요청의 안전한 재시도

새 게임을 포함한 POST/PATCH/DELETE11개에 적용한다. 프론트는 `crypto.randomUUID()`로 행동 번호를 만든다. 터미널 예시:

```bash
ACTION_KEY=$(uuidgen)
curl -i -X POST "http://localhost:8080/api/games/$GAME_ID/rests" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $ACTION_KEY" \
  -d '{"hours":6}'
```

응답을 못 받아 같은 행동을 재시도할 때만 위 명령을 같은 ACTION_KEY로 다시 실행한다. `Idempotency-Replayed: true`이면6시간이 또 흐르지 않고 원래 응답을 받는다. 새로6시간 더 쉬려면 ACTION_KEY를 새로 만든다. 입력을 바꿔 같은 번호로 보내면409다. 키 생략 시에는 중복 방지가 적용되지 않는다. 응답 재사용 시 최신 게임 상태를 GET으로 다시 조회한다.

[최신 중복 요청 검증 결과](../backend/Scrap_Escape_요청중복방지_구현_검증.md): MySQL 전체104개 통과, 서버 재시작 후 응답 복원 확인. 종료 데이터 정리·순위표는 아직 정책 미정이다.

## 화면 연결 준비

`GET /api/games/{gameId}/screen`은 현재 상태·탐색 카드·휴식1~8h/묶음별 행동/수리/치료/탈출 미리보기를 함께 제공한다. 선택창 진입과 행동 완료 후 이 응답으로 화면을 갱신한다. [화면 연동 명세](../frontend/Scrap_Escape_화면_연동_명세.md)에 위치별 필드와 공통 통신 모듈 사용법을 정리했다.

클라이언트 공통 모듈은 서버의 `/js/game-api.mjs`에서 ES module로 가져온다. JavaScript 테스트는 `node --test src/test/javascript/*.test.mjs`. Node는 테스트에만 필요하며 서버는 기존 Spring Boot다.

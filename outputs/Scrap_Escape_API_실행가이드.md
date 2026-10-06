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

장소 ID: 1=고물상, 2=전자제품 매장, 3=마트. 부품 ID: 1=엔진 시동 장치, 2=통신 장치, 3=연료 공급 장치, 4=날개 고정 장치, 5=항법 센서. 해당 판에 선택된 3개 부품만 제작할 수 있다.

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
  "loot": {"materialId":1, "materialName":"철 조각", "quantity":3, "cleanerFound":true, "cleanerAdded":1}
}
```

위 응답은 주요 필드만 발췌한 형식 예시다. 탐색 이외 행동의 loot는 null이다. 재료 0개일 때 materialId와 materialName은 null이며, 세척액은 별도로 발견할 수 있다. 통이 가득 차면 cleanerFound가 true여도 cleanerAdded는 0이다.

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
| 409 | INSUFFICIENT_STAMINA / INSUFFICIENT_CLEANER / INSUFFICIENT_MATERIALS | 자원 부족 |
| 409 | NOT_CLEANABLE / SAME_STORAGE / ALREADY_REPAIRED / GAME_ENDED | 불가능한 행동 |
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
SELECT game_id, elapsed_hours, stamina, cleaner_amount, status FROM game;
```

화면/API에서 발급된 ID를 아래에 넣어 같은 판만 확인한다.

```sql
SET @game_id = '반환된-gameId';
SELECT batch_id, material_id, quantity, storage_type, acquired_hour, item_condition
FROM item_batch WHERE game_id = @game_id;
SELECT * FROM game_repair WHERE game_id = @game_id;
```

탐색 후 묶음 INSERT, 이동·세척 후 행 UPDATE, 폐기·전량 제작 소비 후 DELETE를 비교한다. 만료는 자동 DELETE가 아니라 UNUSABLE로 UPDATE다. 제작 소비와 수리 완료는 같은 트랜잭션이다.

## 7. 검증 기록

- 실제 MySQL 9.6.0의 scrap_escape_test에서 규칙·서비스·HTTP 입력 검사를 수행한다.
- 실제 scrap_escape 서버에 새 판을 만들고 탐색 → 이동 → 세척 → 폐기 → 시간 제한 실패 흐름을 HTTP로 확인했다.
- CREATE/READ/UPDATE/DELETE 결과와 DB의 시간·통계가 일치하는 것을 확인했다.
- 난이도에 따른 평균 탈출 날짜와 실제 게임 UI는 아직 검증하지 않았다.

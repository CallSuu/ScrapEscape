# Scrap Escape — 백엔드 API 명세서

작성 기준:현재 코드·Flyway V1~V8·HTTP 검증. API 수:19개. 기본 주소:`http://localhost:8080`.

[기획 대비 구현 현황](../backend/Scrap_Escape_백엔드_구현현황.md) · [OpenAPI3.1 JSON](openapi.json) · [초보자용 실행 가이드](Scrap_Escape_API_실행가이드.md)

## 1. 공통 규칙

- URL의 `{id}`는 게임 UUID, `{batchId}`·`{partId}`·`{locationId}`는 정수 식별자다. 숫자 경로값은 양수 검증 어노테이션이 없어 음수도 파싱되지만 일치 데이터가 없으면404다. 탐색 본문 locationId는 양수만 허용한다.
- 본문이 있는 요청은 `Content-Type: application/json`. 요청 DTO의 한 필드는 모두 필수이며 null 불가. 휴식 시간은1~8정수.
- 인증·로그인·토큰·이어하기는 범위 밖이다. UUID를 아는 요청자가 해당 판에 접근하는 개인PC용 설계다. API의 gameId 분리는 계정별 접근 제어를 뜻하지 않는다.
- GET은 시간·자원·방 즉시 기회를 변경하지 않는다. 별도 query parameter·페이지네이션은 없다.
- 생성201은 State, 조회200은 각각의객체/배열, 행동200은 ActionResult. DELETE도 응답 본문이 있는200이다.
- HTTP200은 요청 처리가 끝났다는 의미다. 행동 결과가 FAILED일 수 있으므로 `state.status`와 `failureReason`을 확인한다.
- 진행 중인 rulesVersion2만 변경 가능. 종료 판은409 GAME_ENDED, 초기 규칙 판1은409 LEGACY_GAME. 두 경우 조회는 가능하다.
- 게임 행을 FOR UPDATE로 잠그고 한 행동의 자원·재료·시간·결과를 트랜잭션으로 반영한다. 검증 오류409/404 또는 DB500이면 해당 요청 변경은 롤백한다.
- 생성 포함 모든 변경 API 14개는 선택 헤더 `Idempotency-Key`를 지원한다. 프론트엔드는 행동마다 새 UUID를 만들고, 같은 행동의 네트워크 재시도에만 같은 번호를 사용한다. 상세 규칙은 아래를 참고한다.
- 성공한 다른 행동(이동·폐기 등0시간 포함)·defer는 즉시 입장 기회를 끝낸다. 잘못된 요청은 롤백하므로 기회를 소모하지 않는다.
- 게임 시간은 실제 초 타이머가 아니다. elapsedHours=0은1일08:00. 날짜=floor((8+elapsedHours)/24)+1,시각=(8+elapsedHours)%24.
- 샘플은 전용 테스트 DB의 실제 응답이다. 준비한 조건이 포함돼 있으며 게임 UUID·묶음 ID는 정리 후 재사용할 수 없다.

### 1.1. 변경 요청 재전송 방지

- 요청 헤더: `Idempotency-Key: 01234567-89ab-4cde-8fab-0123456789ab`. 정확히 36자 UUID 형식이며 대소문자는 같은 번호로 취급한다. GET에는 적용하지 않는다.
- 같은 번호·같은 HTTP 메서드/실제 경로/검증된 요청 DTO는 원래 상태 코드와 JSON을 반환하고 행동을 다시 실행하지 않는다. JSON 공백·필드 순서·무시되는 추가 필드는 다른 행동으로 보지 않는다. 동시에 도착하거나 서버 재시작 후 재시도해도 동일하다.
- 같은 번호를 다른 경로·게임·입력에 재사용하면409 `IDEMPOTENCY_KEY_REUSED`. 잘못된 번호는400 `INVALID_IDEMPOTENCY_KEY`. 입력 파싱·DTO 검증은 이 처리보다 먼저 수행된다.
- 헤더를 생략하면 기존처럼 매 요청이 별도 행동이다. 다른 번호도 별도 행동이다. 버튼 비활성화와 함께 요청 번호를 사용해야 네트워크 재시도까지 보호된다.
- 정상200/201에는 `Idempotency-Replayed: false`(처음 처리/헤더 없음), `true`(저장된 응답 재사용)를 반환한다. 오류 응답에는 이 헤더를 보장하지 않는다.
- HTTP200+FAILED도 처리 완료 결과이므로 저장한다.400/404/409/500 오류는 저장하지 않으며 행동·요청 기록을 함께 롤백한다. 조건을 해결한 후 같은 번호로 다시 시도할 수 있다.
- 재사용 응답은 **처음 행동 당시 상태**다. 이후 행동이나 게임 종료가 있어도 당시 응답을 돌려준다. 프론트는 `true` 응답이면 `GET /api/games/{id}`로 최신 상태를 다시 조회한 뒤 표시한다. 새 변경 요청의 GAME_ENDED 검사와 구분한다.
- DB: `api_request`에 번호(PK)·요청 지문·게임 FK·원래 JSON·생성 시각 저장. 게임 변경과 같은 트랜잭션이며 실패 시 함께 취소된다. 게임 삭제 시 연결된 기록도 삭제된다. 현재 자동 삭제/TTL은 없고, 재전송 보장은 기록이 남아 있는 동안 유효하다. 종료 데이터 정리 정책(GAP-04)은 별도 결정한다.
- 전체 공통 변경이므로 각 API의 고유 DB 변화에 `api_request INSERT/UPDATE`가 추가된다. 응답 본문 구조·기존 경로는 바뀌지 않았다.
- [실제 휴식 재전송](examples/idempotency-rest-replay.json) · [서버 재시작 후 재전송](examples/idempotency-restart.json) · [충돌 오류](examples/idempotency-conflict.json)

## 2. 전체 API 목록

| ID | 메서드 | 경로 | 기능 | 정상 응답 |
|---|---|---|---|---|
| SE-01 | POST | `/api/games` | 새 게임 생성 | 201 State |
| SE-02 | GET | `/api/games/{id}` | 게임 상태·엔딩 통계 조회 | 200 State |
| SE-03 | GET | `/api/games/{id}/locations` | 탐색 장소 카드 조회 | 200 LocationCard[] |
| SE-04 | POST | `/api/games/{id}/explorations` | 일반 장소 탐색 | 200 ActionResult |
| SE-05 | PATCH | `/api/games/{id}/items/{batchId}/storage` | 재료 묶음 전체 이동 | 200 ActionResult |
| SE-06 | POST | `/api/games/{id}/items/{batchId}/clean` | 재료 한 묶음 세척 | 200 ActionResult |
| SE-07 | DELETE | `/api/games/{id}/items/{batchId}` | 재료 묶음 전체 폐기 | 200 ActionResult |
| SE-08 | POST | `/api/games/{id}/repairs/{partId}` | 부품 제작·즉시 장착 | 200 ActionResult |
| SE-09 | POST | `/api/games/{id}/rests` | 선택 시간 휴식 | 200 ActionResult |
| SE-10 | POST | `/api/games/{id}/treatments` | 의료 키트 사용 | 200 ActionResult |
| SE-11 | POST | `/api/games/{id}/escape` | 수리 완료 후 탈출 | 200 ActionResult |
| SE-12 | GET | `/api/games/{id}/secret-rooms` | 발견한 비밀의 방 목록 | 200 SecretRoomCard[] |
| SE-13 | GET | `/api/games/{id}/secret-rooms/{locationId}` | 비밀의 방 상세·입장 비용 조회 | 200 SecretRoomCard |
| SE-14 | POST | `/api/games/{id}/secret-rooms/{locationId}/defer` | 방 선택창 닫기·지나치기 | 200 ActionResult |
| SE-15 | POST | `/api/games/{id}/secret-rooms/{locationId}/open` | 비밀의 방 선택 입장 | 200 ActionResult |
| SE-16 | GET | `/api/games/{id}/screen` | 화면 상태·카드·안전지대 행동 미리보기 | 200 GameScreen |
| SE-17 | PATCH | `/api/games/{id}/items/storage` | 선택한 여러 묶음 일괄 이동 | 200 ActionResult |
| SE-18 | POST | `/api/games/{id}/items/clean` | 선택한 여러 묶음 일괄 세척 | 200 ActionResult |
| SE-19 | DELETE | `/api/games/{id}/items` | 선택한 여러 묶음 일괄 폐기 | 200 ActionResult |

## 3. API별 상세 명세

아래 curl의 `{id}`·`{batchId}`·`{partId}`·`{locationId}`는 실제 반환된 ID로 바꾼 뒤 실행한다. 공통 오류는5절을 함께 적용한다. 아래 ‘주요409’는 각 작업의 고유 조건이며 GAME_ENDED·LEGACY_GAME은 모든 변경 행동에 공통이다.

### SE-01. 새 게임 생성

`POST /api/games`

- 요청 본문: 없음.
- 성공: **201**, `State`.
- 비용: 초기화: 경과0시간, 1일08:00, 체력15, 세척액1, 키트·오염·열쇠0.
- 처리 규칙: 부품7종 중 중복 없이4개 선택. 폭풍24~48시간 간격·1~3시간 지속 일정을 생성. 새 판 rulesVersion=2. 로그인 없이 UUID로 구분한다.
- DB 변화: game·game_repair·game_recipe·radiation_storm INSERT.
- 주요409: 요청 번호를 다른 행동에 재사용한 경우 IDEMPOTENCY_KEY_REUSED. 헤더가 없으면 고유409 조건 없음.
- [요청·실제 응답 전체 예시](examples/SE-01.json)

```bash
curl -X POST 'http://localhost:8080/api/games'
```

### SE-02. 게임 상태·엔딩 통계 조회

`GET /api/games/{id}`

- 요청 본문: 없음.
- 성공: **200**, `State`.
- 비용: 없음.
- 처리 규칙: 재료·수리·위험·키트·폭풍·열쇠·방·통계를 한 번에 반환한다. 종료 판과 이전 규칙 판도 조회 가능. GET은 시간·즉시 입장 기회를 변경하지 않는다.
- DB 변화: 진행·재료·수리·폭풍·방 SELECT.
- 주요409: 없음.
- [요청·실제 응답 전체 예시](examples/SE-02.json)

```bash
curl -X GET 'http://localhost:8080/api/games/{id}'
```

### SE-03. 탐색 장소 카드 조회

`GET /api/games/{id}/locations`

- 요청 본문: 없음.
- 성공: **200**, `LocationCard[]`.
- 비용: 없음.
- 처리 규칙: 장소6개. 낮·밤·질병·빈칸·체력·폭풍에 따라 예상 비용과 canExplore/불가 이유를 반환한다. 후보 weight=1은 상대 가중치. 미발견 방 secretRoom=null.
- DB 변화: 장소·연결 재료·게임·폭풍·방 SELECT.
- 주요409: 없음.
- [요청·실제 응답 전체 예시](examples/SE-03.json)

```bash
curl -X GET 'http://localhost:8080/api/games/{id}/locations'
```

### SE-04. 일반 장소 탐색

`POST /api/games/{id}/explorations`

- 요청 본문: `{"locationId": 1}` (ExploreRequest)
- 성공: **200**, `ActionResult`.
- 비용: 낮6시간·체력2 / 밤7시간·체력3. 질병이면 각각+2. 기본 오염+10, 연속3회째부터+5, 폭풍시간당+5.
- 처리 규칙: 성공 탐색이어도 재료0 가능. 낮0/1/2/3개=5/15/35/45%, 밤0/2/3/4개=5/20/35/40%. 총1개면1종,2개 이상이면 서로 다른2종으로 분배. 빈칸이1개면 첫 선택 종류만 저장하고 나머지는 현장에 남긴다. 세척액은 독립80%로1회분, 병원은 키트1개 확정(한도까지). 종료 판정 후 진행 중일 때만 열쇠10%·방15%를 독립 판정. 방 자동 입장 없음.
- DB 변화: 재료 INSERT, 시간·체력·오염·질병·보급품·통계 UPDATE, 방 최초 발견 INSERT.
- 주요409: `INVENTORY_FULL` / `INSUFFICIENT_STAMINA`
- [요청·실제 응답 전체 예시](examples/SE-04.json)

```bash
curl -X POST 'http://localhost:8080/api/games/{id}/explorations' \
  -H 'Content-Type: application/json' -d '{"locationId": 1}'
```

### SE-05. 재료 묶음 전체 이동

`PATCH /api/games/{id}/items/{batchId}/storage`

- 요청 본문: `{"storage": "CHEST"}` (MoveRequest)
- 성공: **200**, `ActionResult`.
- 비용: 시간·체력 없음. 성공한 이동은 즉시 입장 기회를 종료.
- 처리 규칙: INVENTORY↔CHEST. 미세척·사용 불가 재료도 이동 가능하며 획득 시각·상태를 유지한다. CLEAN은 같은 종류의 남은 수용량을 먼저 채우고 최대6개로 정리. 목적지에 전체를 넣을 수 없으면 부분 이동 없이409. 합치기 후 입력 batchId가 삭제될 수 있으므로 응답의 새 목록을 사용한다.
- DB 변화: 위치·수량 UPDATE, 전량 합쳐진 행 DELETE.
- 주요409: `SAME_STORAGE` / `STORAGE_FULL`
- [요청·실제 응답 전체 예시](examples/SE-05.json)

```bash
curl -X PATCH 'http://localhost:8080/api/games/{id}/items/{batchId}/storage' \
  -H 'Content-Type: application/json' -d '{"storage": "CHEST"}'
```

### SE-06. 재료 한 묶음 세척

`POST /api/games/{id}/items/{batchId}/clean`

- 요청 본문: 없음.
- 성공: **200**, `ActionResult`.
- 비용: 1시간·체력0·세척액1.
- 처리 규칙: 양쪽 저장소의 DIRTY 묶음 전체. 시작 시48시간 미만이어야 한다. 세척 중48시간이 되어도 대상은 보호한다. 같은 종류 CLEAN은6개씩 정리하고 초과분은 기존 다른 묶음으로 남긴다.
- DB 변화: 상태·수량·자원·시간·통계 UPDATE, 합쳐진 행 DELETE.
- 주요409: `NOT_CLEANABLE` / `INSUFFICIENT_STAMINA` / `INSUFFICIENT_CLEANER`
- [요청·실제 응답 전체 예시](examples/SE-06.json)

```bash
curl -X POST 'http://localhost:8080/api/games/{id}/items/{batchId}/clean'
```

### SE-07. 재료 묶음 전체 폐기

`DELETE /api/games/{id}/items/{batchId}`

- 요청 본문: 없음.
- 성공: **200**, `ActionResult`.
- 비용: 시간·체력 없음. 성공한 폐기는 즉시 입장 기회를 종료.
- 처리 규칙: 상태와 관계없이 자기 판의 묶음 전체를 삭제한다. 일부 수량 폐기 입력 없음. discarded에 개별 수량 누적. 정상 응답은204가 아니라200과 ActionResult다.
- DB 변화: item_batch DELETE, discarded UPDATE.
- 주요409: 추가 고유 코드 없음. 변경 행동은 공통409 적용.
- [요청·실제 응답 전체 예시](examples/SE-07.json)

```bash
curl -X DELETE 'http://localhost:8080/api/games/{id}/items/{batchId}'
```

### SE-08. 부품 제작·즉시 장착

`POST /api/games/{id}/repairs/{partId}`

- 요청 본문: 없음.
- 성공: **200**, `ActionResult`.
- 비용: 3시간·체력 없음.
- 처리 규칙: 해당 판의4개 수리 대상만 가능. 양쪽 저장소의 CLEAN 수량을 합산해 소비한다. 부분 소비 UPDATE·전량 DELETE. 완성품을 슬롯에 넣지 않고 수리 완료로 바꾼다. 전부 수리해도 자동 성공하지 않고 escape.allowed=true가 된다. 기한을 넘으면 수리 행동 후 FAILED 가능.
- DB 변화: 재료 UPDATE/DELETE, 수리·시간 UPDATE.
- 주요409: `ALREADY_REPAIRED` / `INSUFFICIENT_MATERIALS`
- [요청·실제 응답 전체 예시](examples/SE-08.json)

```bash
curl -X POST 'http://localhost:8080/api/games/{id}/repairs/{partId}'
```

### SE-09. 선택 시간 휴식

`POST /api/games/{id}/rests`

- 요청 본문: `{"hours": 6}` (RestRequest)
- 성공: **200**, `ActionResult`.
- 비용: 1~8시간. 회복=hours+(6시간 이상이면2), 최대 체력15.
- 처리 규칙: 4시간 이상 한 번에 쉬면 오염−10·연속 탐색0. 짧은 휴식에는 해당 효과 없음. 체력0·최대 체력에서도 휴식 가능. 질병 치료·기한 초기화는 하지 않으며 만료·72시간·240시간을 진행한다. 폭풍 오염 없음.
- DB 변화: 체력·시간·오염·연속 횟수 UPDATE, 만료·실패 판정.
- 주요409: 추가 고유 코드 없음. 변경 행동은 공통409 적용.
- [요청·실제 응답 전체 예시](examples/SE-09.json)

```bash
curl -X POST 'http://localhost:8080/api/games/{id}/rests' \
  -H 'Content-Type: application/json' -d '{"hours": 6}'
```

### SE-10. 의료 키트 사용

`POST /api/games/{id}/treatments`

- 요청 본문: 없음.
- 성공: **200**, `ActionResult`.
- 비용: 1시간·체력 없음·성공 시 키트1.
- 처리 규칙: 질병 제거·오염−60(최소0), 치료 직후 발병 판정 없음. 질병이 없어도 예방 사용 가능. 먼저1시간 진행해 실패 판정하며 그 시점에72시간 또는240시간에 도달하면 실패를 취소하지 않고 키트도 차감하지 않는다. 성공 치료만 medicalKitsUsed 증가; 실제 질병 치료만 treatmentCount 증가.
- DB 변화: 시간 UPDATE → 실패 판정 → 생존 시 키트·질병·오염·통계 UPDATE.
- 주요409: `INSUFFICIENT_MEDICAL_KIT`
- [요청·실제 응답 전체 예시](examples/SE-10.json)

```bash
curl -X POST 'http://localhost:8080/api/games/{id}/treatments'
```

### SE-11. 수리 완료 후 탈출

`POST /api/games/{id}/escape`

- 요청 본문: 없음.
- 성공: **200**, `ActionResult`.
- 비용: 추가 시간·체력 없음.
- 처리 규칙: 4개 부품 모두 수리한 진행 중 판에서 SUCCESS로 바꾼다. 현재 기한이 이미 도달하면 FAILED를 우선 반환한다. 종료 후 재탈출409. 별도 게임 삭제는 하지 않는다.
- DB 변화: 실패 판정 후 game.status UPDATE.
- 주요409: `REPAIRS_INCOMPLETE`
- [요청·실제 응답 전체 예시](examples/SE-11.json)

```bash
curl -X POST 'http://localhost:8080/api/games/{id}/escape'
```

### SE-12. 발견한 비밀의 방 목록

`GET /api/games/{id}/secret-rooms`

- 요청 본문: 없음.
- 성공: **200**, `SecretRoomCard[]`.
- 비용: 없음.
- 처리 규칙: 발견한 방만 포함. 미발견이면 빈 배열. 열쇠가 없어도 조회 가능. 이미 연 방은 opened=true와 lastVisit 포함.
- DB 변화: game_secret_room·보급품·폭풍 SELECT.
- 주요409: 없음.
- [요청·실제 응답 전체 예시](examples/SE-12.json)

```bash
curl -X GET 'http://localhost:8080/api/games/{id}/secret-rooms'
```

### SE-13. 비밀의 방 상세·입장 비용 조회

`GET /api/games/{id}/secret-rooms/{locationId}`

- 요청 본문: 없음.
- 성공: **200**, `SecretRoomCard`.
- 비용: 없음.
- 처리 규칙: 미발견·다른 판의 방이면404. buttonLabel은 ?. IMMEDIATE/REVISIT별 비용·실행 제한·빈칸·기한 위험을 반환한다. 조회는 열쇠를 소비하거나 기회를 끝내지 않는다.
- DB 변화: 해당 판·장소 방과 자원 SELECT.
- 주요409: 없음.
- [요청·실제 응답 전체 예시](examples/SE-13.json)

```bash
curl -X GET 'http://localhost:8080/api/games/{id}/secret-rooms/{locationId}'
```

### SE-14. 방 선택창 닫기·지나치기

`POST /api/games/{id}/secret-rooms/{locationId}/defer`

- 요청 본문: 없음.
- 성공: **200**, `ActionResult`.
- 비용: 시간·체력·열쇠 없음.
- 처리 규칙: 미개방 방을 기억한 채 즉시 기회를 종료한다. 이미 기억한 미개방 방에 반복 호출 가능. 성공한 defer는 다른 방에 남은 즉시 기회도 끝낸다. 이후 일반 탐색 가능, 해당 방은 REVISIT로 입장한다. 개방 완료 결과창 X는 이 API 대신 화면만 닫는다.
- DB 변화: 미개방 방 immediate_available UPDATE.
- 주요409: `ROOM_ALREADY_OPENED`
- [요청·실제 응답 전체 예시](examples/SE-14.json)

```bash
curl -X POST 'http://localhost:8080/api/games/{id}/secret-rooms/{locationId}/defer'
```

### SE-15. 비밀의 방 선택 입장

`POST /api/games/{id}/secret-rooms/{locationId}/open`

- 요청 본문: `{"mode": "REVISIT"}` (OpenRoomRequest)
- 성공: **200**, `ActionResult`.
- 비용: IMMEDIATE:1시간·체력1 / REVISIT:낮4시간·체력2,밤5시간·체력3. REVISIT 질병이면 각각+2. 모두 열쇠1.
- 처리 규칙: IMMEDIATE는 발견 후 다른 유효 행동·defer 전에만 가능. REVISIT는 발견한 미개방 방이면 선택 가능. 한 장소당1회만 개방. 재료3~4개50%, 보급품30%(세척액2/키트1 반반), 미세척 인벤토리 손실1~2개10%, 오염+15가10%. 손실 대상이 없으면 오염으로 대체. 보관함·CLEAN·UNUSABLE 보호. 보상은 새 획득 시각의 DIRTY 묶음, 빈칸 없으면 남김. 일반 탐색의 재료·보급품·열쇠를 추가 지급하지 않는다. IMMEDIATE 기본 오염0, REVISIT 기본10+연속 보정; 두 모드 모두 폭풍+나쁜 결과를 합산. 발병·만료·실패 기한을 판정한다.
- DB 변화: 열쇠·자원·시간·오염·통계 UPDATE, 재료 INSERT/UPDATE/DELETE, 방 개방 이력 UPDATE.
- 주요409: `ROOM_ALREADY_OPENED` / `IMMEDIATE_UNAVAILABLE` / `INSUFFICIENT_SECRET_KEY` / `INSUFFICIENT_STAMINA`
- [요청·실제 응답 전체 예시](examples/SE-15.json)

```bash
curl -X POST 'http://localhost:8080/api/games/{id}/secret-rooms/{locationId}/open' \
  -H 'Content-Type: application/json' -d '{"mode": "REVISIT"}'
```

### SE-16. 화면 스냅샷·행동 미리보기

`GET /api/games/{id}/screen`

- 요청 본문·별도 헤더 없음. 정상200 `GameScreen`, 잘못된 UUID400, 없는 게임404, 예기치 못한 오류500.
- `state`: 기존 State 전체. `locations`: 기존 장소 카드6개. `actions`: 휴식1~8시간 선택8개, 양쪽 저장소 묶음별 이동·세척·폐기, 선택 부품별 수리, 치료·탈출 미리보기.
- 한 읽기 전용 트랜잭션으로 조회한다. INSERT/UPDATE/DELETE 없음. 시간·체력·재료 만료 처리·즉시 입장 기회 소모 없음. 기존 GET/변경 API는 호환 유지.
- 종료·이전 규칙 판도 조회 가능하다. 해당 행동은 `allowed=false`, 원인 코드·설명 제공, `projected=null`.
- 실행 가능한 행동은 `allowed=true`, 원인 코드·설명null. `projected`로 예상 경과시간·체력·오염·세척액·키트·질병·상태·실패 사유·만료될 묶음 목록을 제공한다. 재료 소모 상세 목록/병합 후 묶음ID/수리 통계 전체까지 예측하는 객체는 아니다. 실행 후 전체 screen을 재조회한다.
- `actions.rests[]`: `{hours,preview}`. 6시간 이상 보너스·체력 상한·4시간 이상 오염 감소 반영.
- `actions.items[]`: `{batchId,moveTo,move,clean,discard}`. 이동은 현재 반대 저장소로 전량 이동. CLEAN 합칠 공간까지 반영한다. 세척은 상태→체력→세척액 순서의 제한을 반영한다.
- `actions.repairs[]`: `{partId,preview}`. 양쪽 보관함의 CLEAN 재료를 합산하고 수리 완료·재료 부족을 구분한다.
- `actions.treatment`·`escape`: `SafeActionPreview`. 치료 완료 시 기한에 도달하면 FAILED를 예측하고, 키트·오염·질병은 치료되지 않은 상태로 표시한다.
- `projected.expiringBatchIds`: 해당 행동 후 사용할 수 없게 되는 미세척 묶음ID. 선택 세척 재료는 보호되고 폐기 대상은 목록에서 제외한다.
- 기한 초과는 차단하지 않는다. `projected.status=FAILED` 및 `failureReason`으로 경고한다. 두 기한 초과 시 TIME_LIMIT 우선. 탐색 카드는 남은 기한과 durationHours를 비교하고 방은 기존 deadlineRisk를 사용한다.
- 조회는 상태를 예약/잠금 유지하지 않는다. 이후 다른 탭에서 행동하면 달라질 수 있으므로 실제 변경 API가 현재 자원·상태를 다시 검증한다.
- [초기 화면 실제 응답](examples/SE-16.json) · [기한·만료 경계](examples/screen-deadline.json) · [종료 판](examples/screen-ended.json)

```bash
curl "http://localhost:8080/api/games/$GAME_ID/screen"
```

추가 DTO 7개: `GameScreen`, `SafeActions`, `SafeActionPreview`, `ActionProjection`, `RestPreview`, `ItemActionPreview`, `RepairActionPreview`. 모든 필드/nullable/배열 타입은 [OpenAPI](openapi.json)에 정의한다. 프론트 매핑과 공통 모듈 사용법은 [화면 연동 명세](../frontend/Scrap_Escape_화면_연동_명세.md)를 참고한다.

## 4. 요청·응답 데이터 사전

필드는 별도 표시가 없으면 null이 아니며 응답에 포함된다. 아래 필드들은 OpenAPI의 components.schemas와 동일하다. 요청 본문에는 문서에 정의된 필드만 보내는 것을 권장한다. 정의되지 않은 필드는 현재 서버의 기본 JSON 설정에서 무시된다.

### OpenRoomRequest

IMMEDIATE 또는 REVISIT를 지정한다. 이미 끝난 즉시 기회는 사용할 수 없다.

| 필드 | JSON 자료형 | null 허용 | 설명 |
|---|---|---|---|
| `mode` | RoomMode | 아니오 | 발견 직후 IMMEDIATE, 기억한 방 REVISIT. |

### SecretFindings

일반 탐색의 독립 열쇠·방 발견 결과. 자동 입장은 하지 않는다.

| 필드 | JSON 자료형 | null 허용 | 설명 |
|---|---|---|---|
| `keyFound` | boolean | 아니오 | 이번 일반 탐색에서 열쇠를 발견했는지. |
| `keysAdded` | int | 아니오 | 실제 저장량0/1. 한도 초과는0. |
| `roomDiscovered` | boolean | 아니오 | 해당 장소의 방을 처음 발견했는지. |
| `roomLocationId` | Long | 예 | 새로 발견한 장소 ID, 미발견이면 null. |

### SecretStatistics

방 발견·개방·열쇠·결과 누계. 남은 열쇠는 SecretState.keyAmount에 있다.

| 필드 | JSON 자료형 | null 허용 | 설명 |
|---|---|---|---|
| `discoveredRooms` | int | 아니오 | 발견한 서로 다른 방 수0~6. |
| `openedRooms` | int | 아니오 | 한 번 개방한 방 수0~6. |
| `unopenedRooms` | int | 아니오 | 발견했지만 미개방인 방 수. |
| `keysAcquired` | int | 아니오 | 실제 보유량에 저장한 열쇠 총수량. |
| `keysUsed` | int | 아니오 | 입장에 소비한 열쇠 수. |
| `goodOutcomes` | int | 아니오 | 재료/세척액/의료 키트 결과 횟수. |
| `badOutcomes` | int | 아니오 | 재료 손실/추가 오염 결과 횟수. |
| `materialsCollected` | int | 아니오 | 방에서 실제 저장한 개별 재료 수량. |
| `materialsLost` | int | 아니오 | 방에서 잃은 개별 재료 수량. |

### SecretState

비밀의 열쇠·발견한 방·누적 통계. 엔딩에서도 같은 구조를 읽는다.

| 필드 | JSON 자료형 | null 허용 | 설명 |
|---|---|---|---|
| `keyAmount` | int | 아니오 | 남은 열쇠0~2. |
| `keyCapacity` | int | 아니오 | 열쇠 한도2. |
| `keyProbabilityPercent` | int | 아니오 | 일반 탐색 열쇠 발견10%. |
| `discoveryProbabilityPercent` | int | 아니오 | 해당 미발견 방 발견15%. |
| `rooms` | List<SecretRoomCard> | 아니오 | 발견한 방만 포함. 열쇠 부족·이미 개방도 조회 가능. |
| `statistics` | SecretStatistics | 아니오 | 확장 누적 통계. 엔딩에도 동일. |

### RoomVisitPreview

해당 방의 현재 시각·질병 상태에 따른 실행 조건과 계산 비용. allowed=false이어도 정보는 조회 가능.

| 필드 | JSON 자료형 | null 허용 | 설명 |
|---|---|---|---|
| `allowed` | boolean | 아니오 | 해당 모드 실행 가능 여부. |
| `unavailableReason` | String | 예 | 불가 이유 또는 null. |
| `durationHours` | int | 아니오 | 방 개방 포함 시간 비용. |
| `staminaCost` | int | 아니오 | 체력 비용. |
| `keyCost` | int | 아니오 | 항상 열쇠1. |
| `baseContamination` | int | 아니오 | 즉시0. 재방문은10 또는 연속 보정 포함15. |
| `stormExposureHours` | int | 아니오 | 현재 구간 예상 노출 시간. |
| `stormContamination` | int | 아니오 | 폭풍 노출×5. |
| `expectedContaminationIncrease` | int | 아니오 | 결과 추첨 전 기본+폭풍. 상한 적용 전. |
| `possibleExtraContamination` | int | 아니오 | 나쁜 결과로 추가 가능한15. 항상 더해지는 값은 아니다. |
| `rewardFreeSlots` | int | 아니오 | 재료 보상을 담을 수 있는 현재 빈 슬롯 수. |
| `deadlineRisk` | boolean | 아니오 | 방문 완료 시240시간 또는 치료 기한 도달 예상. true여도 실행은 허용하고 실패할 수 있다. |

### SecretRoomCard

발견한 방만 반환한다. buttonLabel은 ?이며 실제 입장은 별도 open API로 선택한다.

| 필드 | JSON 자료형 | null 허용 | 설명 |
|---|---|---|---|
| `locationId` | long | 아니오 | 방이 있는 탐색 장소 ID. |
| `locationName` | String | 아니오 | 장소 이름. |
| `buttonLabel` | String | 아니오 | ? 한 글자. |
| `discoveredHour` | int | 아니오 | 방 발견 경과 시간. |
| `opened` | boolean | 아니오 | 개방 완료 여부. |
| `immediateAvailable` | boolean | 아니오 | 즉시 기회가 남았는지. 실제 실행 여부는 immediate.allowed로 판단. |
| `immediate` | RoomVisitPreview | 아니오 | 즉시 입장 미리보기. |
| `revisit` | RoomVisitPreview | 아니오 | 기억한 방 재방문 미리보기. |
| `openedHour` | Integer | 예 | 개방 완료 경과 시간, 미개방이면 null. |
| `lastVisit` | SecretRoomOutcome | 예 | 개방 결과 이력, 미개방이면 null. |

### SecretRoomOutcome

방 입장 결과. 이미 연 방의 lastVisit에도 동일 구조를 제공한다.

| 필드 | JSON 자료형 | null 허용 | 설명 |
|---|---|---|---|
| `locationId` | long | 아니오 | 개방 장소 ID. |
| `mode` | RoomMode | 아니오 | IMMEDIATE/REVISIT. |
| `outcome` | RoomOutcome | 아니오 | MATERIALS/CLEANER/MEDICAL_KIT/MATERIAL_LOSS/CONTAMINATION. |
| `durationHours` | int | 아니오 | 사용 시간. |
| `staminaCost` | int | 아니오 | 사용 체력. |
| `contaminationIncrease` | int | 아니오 | 기본+폭풍+나쁜 결과의 계산 증가량(100 상한 전). |
| `stormExposureHours` | int | 아니오 | 이번 방문 노출 시간. |
| `loot` | Loot | 아니오 | 보상 구조. 손실·오염이면 재료0·빈 배열·보급0. |
| `lostQuantity` | int | 아니오 | 잃은 미세척 재료 수량0~2. 개별 손실 batchId 목록은 제공하지 않는다. |

### ExploreRequest

탐색할 장소 ID 하나를 지정한다.

| 필드 | JSON 자료형 | null 허용 | 설명 |
|---|---|---|---|
| `locationId` | Long | 아니오 | 양의 정수. 현재1~6만 유효. |

### MoveRequest

현재 재료 묶음 전체의 목적지. 일부 수량만 이동하는 입력은 없다.

| 필드 | JSON 자료형 | null 허용 | 설명 |
|---|---|---|---|
| `storage` | Storage | 아니오 | 목적지 INVENTORY/CHEST. |

### RestRequest

정수1~8시간. 실제 시간 대기 없이 게임 시간을 진행한다.

| 필드 | JSON 자료형 | null 허용 | 설명 |
|---|---|---|---|
| `hours` | Integer | 아니오 | 1~8 정수. 소수 불가. |

### Item

한 재료 묶음. 최대6개, 미세척 묶음의 획득 시각은 별도로 보존한다.

| 필드 | JSON 자료형 | null 허용 | 설명 |
|---|---|---|---|
| `batchId` | long | 아니오 | 묶음 ID. 합치기로 행이 삭제되면 해당 ID는 더 이상 유효하지 않다. |
| `materialId` | long | 아니오 | 재료 종류 ID. |
| `name` | String | 아니오 | 재료 이름. |
| `quantity` | int | 아니오 | 한 묶음 수량1~6. |
| `storage` | Storage | 아니오 | INVENTORY 또는 CHEST. |
| `acquiredHour` | int | 아니오 | 획득 완료 시점의 게임 경과 시간. |
| `condition` | Condition | 아니오 | DIRTY/CLEAN/UNUSABLE. |
| `expiresInHours` | Integer | 예 | DIRTY의 남은48시간 유효 기간. 다른 상태는 null. |

### Ingredient

조합 재료와 양쪽 저장소에서 합산한 세척 완료 수량.

| 필드 | JSON 자료형 | null 허용 | 설명 |
|---|---|---|---|
| `materialId` | long | 아니오 | 재료 ID. |
| `name` | String | 아니오 | 재료 이름. |
| `requiredQuantity` | int | 아니오 | 이 부품의 필요 개별 수량. |
| `availableQuantity` | int | 아니오 | 인벤토리와 보관함의 CLEAN 수량 합계. |

### Repair

해당 판에 선택된 수리 대상과 고정 조합법. canCraft는 재료·수리·진행 조건을 판정한다.

| 필드 | JSON 자료형 | null 허용 | 설명 |
|---|---|---|---|
| `partId` | long | 아니오 | 부품 ID. |
| `name` | String | 아니오 | 해당 판에 저장한 부품 이름. |
| `repaired` | boolean | 아니오 | 수리 완료 여부. |
| `canCraft` | boolean | 아니오 | 진행 중·미수리·세척 재료 충족 여부. 기한 초과 경고까지 포함하는 값은 아니다. |
| `ingredients` | List<Ingredient> | 아니오 | 고정 조합법과 세척 보유량. |

### Statistics

기본 게임 누적 통계. 방 보상 실제 저장분은 collected에도 포함하며 방 손실은 discarded와 구분한다.

| 필드 | JSON 자료형 | null 허용 | 설명 |
|---|---|---|---|
| `explorations` | int | 아니오 | 일반 장소 탐색 횟수. 방 방문 제외. |
| `dayExplorations` | int | 아니오 | 낮 출발 일반 탐색 횟수. |
| `nightExplorations` | int | 아니오 | 밤 출발 일반 탐색 횟수. |
| `emptyExplorations` | int | 아니오 | 발견 재료 총수량0인 일반 탐색 횟수. |
| `collected` | int | 아니오 | 일반 탐색+방 보상에서 실제 보관한 개별 재료 누계. |
| `cleaned` | int | 아니오 | 세척한 개별 재료 누계. |
| `discarded` | int | 아니오 | 직접 폐기한 개별 수량. 제작 소비·방 손실 제외. |
| `repairedParts` | int | 아니오 | 수리 완료 개수. |
| `diseaseCount` | int | 아니오 | 새 발병 횟수. |
| `treatmentCount` | int | 아니오 | 질병을 치료한 횟수. 예방 사용은 제외. |
| `medicalKitsUsed` | int | 아니오 | 성공적으로 사용한 키트 수. |
| `maxContamination` | int | 아니오 | 최대 도달 오염도. |
| `stormExposureHours` | int | 아니오 | 일반 탐색+방 방문 폭풍 노출 시간 누계. |

### Storm

시작08:00으로부터의 경과 시간. 실제 날짜나 Unix 시각이 아니다.

| 필드 | JSON 자료형 | null 허용 | 설명 |
|---|---|---|---|
| `startHour` | int | 아니오 | 폭풍 시작 경과 시간. |
| `endHour` | int | 아니오 | 폭풍 종료 경과 시간. 종료 시각은 노출에 포함하지 않는다. |

### ActionAvailability

특정 행동의 가능 여부·제한 이유·시간·체력 비용.

| 필드 | JSON 자료형 | null 허용 | 설명 |
|---|---|---|---|
| `allowed` | boolean | 아니오 | 실행 가능 여부. |
| `unavailableReason` | String | 예 | 불가 이유. 가능하면 null. |
| `durationHours` | int | 아니오 | 게임 시간 비용. |
| `staminaCost` | int | 아니오 | 체력 비용. |

### State

한 판의 최신 상태. 생성·조회는 직접 반환하고 행동 응답은 state 필드에 포함한다.

| 필드 | JSON 자료형 | null 허용 | 설명 |
|---|---|---|---|
| `gameId` | UUID | 아니오 | 판별 UUID. 로그인 계정 ID가 아니다. |
| `status` | Status | 아니오 | RUNNING/SUCCESS/FAILED. |
| `elapsedHours` | int | 아니오 | 게임 시작 후 경과 시간. 실제 시간은 자동 반영하지 않는다. |
| `day` | int | 아니오 | 시작1일 차08:00을 반영한 날짜 번호. |
| `hour` | int | 아니오 | 현재 시각0~23. |
| `remainingHours` | int | 아니오 | 생존 제한까지 남은 시간. 새 규칙240시간, 이전 규칙 판168시간. |
| `night` | boolean | 아니오 | 20:00 이상~다음06:00 미만. |
| `stamina` | int | 아니오 | 현재 체력0~15. |
| `maxStamina` | int | 아니오 | 최대 체력15. |
| `cleanerAmount` | int | 아니오 | 세척액0~8회분. |
| `cleanerCapacity` | int | 아니오 | 세척액 한도8. |
| `inventoryCapacity` | int | 아니오 | 새 판4칸. 이전 규칙 조회는3칸. |
| `chestCapacity` | int | 아니오 | 새 판16칸. 이전 규칙 조회는8칸. |
| `inventory` | List<Item> | 아니오 | 현재 소지 재료 묶음. |
| `chest` | List<Item> | 아니오 | 비행기 보관함의 묶음. |
| `repairs` | List<Repair> | 아니오 | 새 판의 수리 대상4개. 이전 판은 기존 목표 보존. |
| `statistics` | Statistics | 아니오 | 기본 누적 통계. |
| `stackLimit` | int | 아니오 | 묶음 최대6개. |
| `medicalKitAmount` | int | 아니오 | 의료 키트0~2개. |
| `medicalKitCapacity` | int | 아니오 | 키트 한도2. |
| `contamination` | int | 아니오 | 플레이어 오염도0~100. 재료 만료와 별도. |
| `diseased` | boolean | 아니오 | 질병 유무. |
| `treatmentRemainingHours` | Integer | 예 | 질병 미치료 실패까지 남은 시간. 질병 없으면 null. |
| `consecutiveExplorations` | int | 아니오 | 일반 탐색·방 재방문 누계. 한 번에4시간 이상 휴식으로 초기화. |
| `currentStorm` | Storm | 예 | 현재 진행 중 폭풍. 없으면 null. |
| `nextStorm` | Storm | 예 | 현재 시각 이후 다음 폭풍. 없으면 null. |
| `treatment` | ActionAvailability | 아니오 | 키트 사용 가능 여부와1시간 비용. |
| `escape` | ActionAvailability | 아니오 | 모든 부품 수리 후 직접 탈출할 수 있는지. |
| `failureReason` | String | 예 | TIME_LIMIT/UNTREATED_DISEASE 또는 null. |
| `rulesVersion` | int | 아니오 | 새 판2. 이전 규칙 판1은 조회만 가능. |
| `secrets` | SecretState | 아니오 | 열쇠·방·확장 통계. |

### ActionResult

상태 변경 행동 응답. HTTP200이어도 state.status가 FAILED일 수 있다.

| 필드 | JSON 자료형 | null 허용 | 설명 |
|---|---|---|---|
| `message` | String | 아니오 | 사용자 안내 문구. 논리 분기에는 code/status/구조화 필드를 사용한다. |
| `state` | State | 아니오 | 행동 후 최신 상태. |
| `loot` | Loot | 예 | 탐색·방 입장 보상. 다른 행동은 null. |
| `secretFindings` | SecretFindings | 예 | 일반 탐색만 제공. 종료된 탐색은 발견false/0. 다른 행동은 null. |
| `secretRoom` | SecretRoomOutcome | 예 | 방 입장 결과. 다른 행동은 null. |

### MaterialLoot

재료 종류별 발견 수량과 실제 저장 수량.

| 필드 | JSON 자료형 | null 허용 | 설명 |
|---|---|---|---|
| `materialId` | long | 아니오 | 발견 재료 ID. |
| `name` | String | 아니오 | 재료 이름. |
| `quantity` | int | 아니오 | 이 종류 발견 수량. |
| `collectedQuantity` | int | 아니오 | 이 종류 실제 저장 수량. |

### Loot

발견 수량·실제 저장 수량·현장에 남긴 수량·보급품 결과.

| 필드 | JSON 자료형 | null 허용 | 설명 |
|---|---|---|---|
| `quantity` | int | 아니오 | 발견 재료 총수량. |
| `collectedQuantity` | int | 아니오 | 실제로 슬롯에 보관한 수량. |
| `leftBehindQuantity` | int | 아니오 | quantity-collectedQuantity. 공간 부족으로 남긴 수량. |
| `materials` | List<MaterialLoot> | 아니오 | 종류별 결과. 일반 탐색 최대2종, 방 재료 보상1종, 없으면 빈 배열. |
| `cleanerFound` | boolean | 아니오 | 세척액을 발견했는지. 통이 가득 차도 true 가능. |
| `cleanerAdded` | int | 아니오 | 실제 충전량. |
| `medicalKitsAdded` | int | 아니오 | 실제 저장한 키트 수. |

### Candidate

장소의 재료 후보. weight는 상대 가중치이며 백분율이 아니다.

| 필드 | JSON 자료형 | null 허용 | 설명 |
|---|---|---|---|
| `materialId` | long | 아니오 | 후보 재료 ID. |
| `name` | String | 아니오 | 후보 이름. |
| `weight` | int | 아니오 | 현재 모든 후보1. 1%라는 뜻이 아니다. |

### RewardProbability

총 획득 재료 개수별 확률. 재료 종류별 확률과 다르다.

| 필드 | JSON 자료형 | null 허용 | 설명 |
|---|---|---|---|
| `quantity` | int | 아니오 | 총 재료 개수. |
| `probabilityPercent` | int | 아니오 | 그 개수가 나올 확률(%). |

### LocationCard

탐색 장소 정보와 현재 상태 기준 비용·보상·실행 제한.

| 필드 | JSON 자료형 | null 허용 | 설명 |
|---|---|---|---|
| `locationId` | long | 아니오 | 탐색 장소 ID. |
| `name` | String | 아니오 | 장소 이름. |
| `description` | String | 아니오 | 설명. 이미지 URL은 아직 포함하지 않는다. |
| `materials` | List<Candidate> | 아니오 | 이 장소의 재료 후보. |
| `durationHours` | int | 아니오 | 현재 낮·밤·질병 기준 시간. |
| `staminaCost` | int | 아니오 | 현재 기준 체력 비용. |
| `rewards` | List<RewardProbability> | 아니오 | 총 재료 수량 확률 분포. |
| `cleanerProbabilityPercent` | int | 아니오 | 세척액1회분 발견 확률80%. |
| `canExplore` | boolean | 아니오 | 게임·슬롯·체력 기준 탐색 가능 여부. |
| `unavailableReason` | String | 예 | 탐색 불가 이유 또는 null. |
| `medicalKitReward` | int | 아니오 | 병원1, 나머지0. 한도 도달 시 실제 지급은0. |
| `baseContamination` | int | 아니오 | 일반 탐색 기본+10. |
| `consecutiveContamination` | int | 아니오 | 3회째부터 추가+5, 그 외0. |
| `stormExposureHours` | int | 아니오 | 지금 출발할 경우 예상 노출 시간. |
| `stormContamination` | int | 아니오 | 노출시간×5. |
| `expectedContaminationIncrease` | int | 아니오 | 기본+연속+폭풍의 상한 적용 전 증가량. 실제 오염도는100으로 제한. |
| `diseaseProbabilityAfterExploration` | int | 아니오 | 증가 후 오염도 기준 발병%. 이미 질병이면 추가 판정하지 않아0. |
| `secretRoom` | SecretRoomCard | 예 | 미발견이면 null. 발견한 해당 장소의 방 정보. |

### 열거형

| 타입 | 값 |
|---|---|
| Storage | `INVENTORY` / `CHEST` |
| Condition | `DIRTY` / `CLEAN` / `UNUSABLE` |
| Status | `RUNNING` / `SUCCESS` / `FAILED` |
| RoomMode | `IMMEDIATE` / `REVISIT` |
| RoomOutcome | `MATERIALS` / `CLEANER` / `MEDICAL_KIT` / `MATERIAL_LOSS` / `CONTAMINATION` |

## 5. 오류 명세

```json
{"code":"INSUFFICIENT_MATERIALS","message":"세척된 재료가 부족합니다."}
```

| HTTP | code | 의미 |
|---|---|---|
|400|INVALID_IDEMPOTENCY_KEY|요청 번호가 정확한 UUID 형식이 아님|
|409|IDEMPOTENCY_KEY_REUSED|같은 요청 번호를 다른 행동·게임·입력에 재사용|
|400|INVALID_REQUEST|잘못된 UUID·JSON·필드 누락·null·자료형·enum·시간 범위|
|404|NOT_FOUND|없는 게임·자기 판이 아닌 재료·대상이 아닌 부품·없는 장소·미발견 방|
|409|GAME_ENDED|종료된 판 변경 시도|
|409|LEGACY_GAME|초기 규칙 판 변경 시도|
|409|INVENTORY_FULL|일반 탐색 시작 시 인벤토리 가득 참|
|409|STORAGE_FULL|묶음 전체를 목적지에 이동할 공간 부족|
|409|INSUFFICIENT_STAMINA|탐색·세척·방 입장 체력 부족|
|409|INSUFFICIENT_CLEANER|세척액 부족|
|409|INSUFFICIENT_MATERIALS|세척 재료 부족|
|409|INSUFFICIENT_MEDICAL_KIT|의료 키트 부족|
|409|NOT_CLEANABLE|DIRTY가 아니거나 이미48시간 경과|
|409|SAME_STORAGE|현재와 동일 위치로 이동|
|409|ALREADY_REPAIRED|이미 수리 완료|
|409|REPAIRS_INCOMPLETE|모든 부품 수리 전 탈출|
|409|ROOM_ALREADY_OPENED|이미 개방한 방에 입장·defer|
|409|IMMEDIATE_UNAVAILABLE|즉시 입장 기회 종료|
|409|INSUFFICIENT_SECRET_KEY|열쇠 부족|
|404/405/415 등|HTTP_ERROR|없는URL·잘못된HTTP메서드·지원하지 않는Content-Type 등 프레임워크 오류|
|500|INTERNAL_ERROR|예상하지 못한 서버·DB 오류. DB 변경 롤백|

여러 조건이 동시에 잘못되면 서버 검증 순서상 먼저 발견한 오류 하나만 반환한다. 기한 초과는 일반적으로409가 아니라200+FAILED다. 현재 두 기한을 한 행동에서 모두 넘으면 TIME_LIMIT를 우선하며, 사용자가 확정한 시간 제한 우선 규칙이다(GAP-01 유지).

## 6. 고정 데이터 ID

| 장소 ID | 이름 | 재료 ID | 보급 |
|---|---|---|---|
|1|고물상|1,2,6|세척액80%|
|2|전자제품 매장|3,4,7|세척액80%|
|3|마트|5,6,8|세척액80%|
|4|정비소|1,2,9|세척액80%|
|5|통신 시설|3,4,10|세척액80%|
|6|병원|5,8|의료 키트1확정·세척액80%|

| 재료 ID | 이름 |
|---|---|
|1|철 조각|
|2|기어|
|3|구리 전선|
|4|회로 기판|
|5|플라스틱 부품|
|6|고무 부품|
|7|배터리 셀|
|8|필터|
|9|내열 합금|
|10|신호 모듈|

| 부품 ID | 이름 | 조합법(재료ID:수량) |
|---|---|---|
|1|엔진 시동 장치|1:3,2:2,3:2,7:1|
|2|동력 전달 장치|1:3,2:3,6:2|
|3|연료 공급 장치|1:2,6:3,5:2,8:1|
|4|냉각 장치|3:2,6:2,5:2,9:2|
|5|비행 제어 장치|4:3,3:2,5:2,10:1|
|6|통신·항법 장치|4:2,3:3,7:2,10:1|
|7|기체 보강 장치|1:4,9:3,6:2|

장소·재료 전체/全7개 조합법을 별도 URL로 조회하는 API는 없다. 화면에 필요한 장소 정보는SE-03,해당 판4개 수리 조합은State.repairs로 받는다.

## 7. 별도 API 없이 통합 처리되는 백엔드 기능

| 내부 기능 | 실행 계기·반환 위치 |
|---|---|
|낮·밤·체력 비용|탐색/방 입장 출발 시 판단. SE-03·SE-13 미리보기|
|재료48시간 만료|시간 소비 행동의 finish에서 UNUSABLE로 UPDATE|
|세척 재료 자동 합치기|세척·CLEAN 이동 안에서 실행. 별도 합치기 API 없음|
|오염·발병|탐색·방 종료 시. State와Statistics에 반영|
|치료 기한·240시간 실패|시간 소비 행동·탈출의 finish에서 판정|
|폭풍 생성·노출 계산|새 판 생성 및 탐색/방 구간 비교. State.currentStorm/nextStorm|
|열쇠·방 무작위 발견|일반 탐색 후 진행 중일 때만. secretFindings|
|엔딩 통계|별도 통계 API 없이 SE-02의 statistics와secrets.statistics|
|DB 업그레이드|서버 시작 시Flyway V1~V6. HTTP관리API 없음|

## 8. 아직 제공하지 않는 계약

모든 재료/전체 조합법용 독립 카탈로그 API, 종료 판 삭제·자동정리 API, 안전지대 행동 미리보기는 SE-16에 구현되어 있다. 별도의 확률 보상 결과 확정 미리보기는 제공하지 않는다. 일부는 범위 미정·연동 보완이며 핵심 동작 실패를 뜻하지 않는다. 자세한 구분은 구현 현황 문서에 기록했다.

## 9. 검증 근거

Controller의16개HTTP작업,GameModels의공개요청·응답record,Service/Repository의검증·트랜잭션·수량·시간규칙을 대조했다. 실제 HTTP응답을 예시 파일로 수집하고 OpenAPI스키마와 맞는지 확인한다. 구체적 실행 결과는 동봉된 구현 현황의 검증 항목을 참고한다.

## SE-17. 여러 재료 묶음 일괄 이동

`PATCH /api/games/{id}/items/storage`

```json
{"batchIds":[101,102],"storage":"CHEST"}
```

위 ID는 구조 설명용이며 실행할 때 해당 판에서 조회한 batchId를 사용한다. batchIds는1~20개의 중복 없는 양수이며 각 값과 storage는 null 불가다. 출발 위치가 섞이면 이미 목적지에 있는 항목이 있으므로409 SAME_STORAGE. INVENTORY→CHEST와 CHEST→INVENTORY 모두 지원한다. 1개 입력도 허용하며 기존 SE-05는 유지한다.

- 비용:0시간·체력0. 성공한 이동은 방의 즉시 입장 기회를 종료한다.
- 처리: 해당 게임 행을 잠그고 모든 항목의 소유 판/출발 위치를 확인한 뒤 동일 트랜잭션에서 이동한다. DIRTY/UNUSABLE은 별도 묶음, CLEAN은 같은 재료의 목적지 여유를 채우고 최대6개로 정리한다. source batchId가 병합으로 사라질 수 있다.
- DB: item_batch 위치/수량 UPDATE, 소진된 CLEAN source DELETE, 방 즉시 기회 UPDATE, Idempotency-Key 사용 시 api_request INSERT/UPDATE. 테이블 신설은 없다.
- 성공:200 ActionResult, message에 이동한 선택 묶음 수. loot/secretFindings/secretRoom은null. 프론트는 GET screen으로 최신 목록을 확인하고 성공한 경우만 야영지로 복귀한다.
- 오류:400 INVALID_REQUEST(빈/20개 초과/중복/음수/0/null/잘못된 storage),404 NOT_FOUND(판/묶음 없음·다른 판 묶음),409 SAME_STORAGE/STORAGE_FULL/GAME_ENDED/LEGACY_GAME 및 공통 Idempotency 오류. 어느 항목이든 실패하면 앞서 반영한 이동·방 기회·요청 기록까지 전체 롤백한다.
- 재시도: 같은 UUID·같은 입력 배열 순서와 목적지는 원래 응답을 재사용한다. source가 병합되어 없어졌더라도 재실행하지 않는다. 다른 순서도 다른 DTO이므로 같은 키로 재사용하지 않는다.
- [실제 HTTP 예시](examples/SE-17.json). MySQL 통합 검증은 전체 이동·후속 공간 부족 롤백·CLEAN 병합·원래 응답 재사용·중복/타 판/종료/목적지 검증을 포함한다.

## 체력15 규칙과 예시 갱신

MAX_STAMINA=15, 새 판 초기15, 휴식 상한15로 변경했다. 낮/밤/질병/세척 비용과 휴식 회복 공식은 유지한다. V7은 기존 stamina 값을 보존하고 DB 기본값/제약만0~15로 바꾼다. 기존 판을 자동 회복시키지 않는다. 새로고침/서버 재시작으로 마이그레이션을 적용한다.

SE-01~SE-17 정상 예시와 OpenAPI 정상 예시는 현재 서버에서 다시 수집했다. 추가 경계/중복 예시 파일은 과거 시점의 체력10 기록이 남아 있으며 해당 note를 읽는다. 요청 재사용 응답도 원래 시점의 최대 체력일 수 있으므로 항상 새 screen을 조회한다.

## 세척 보급 조정 — 2026-10-28

세척액은 최대8회분, 일반 탐색에서 독립80%로1회분 획득한다. 초기 지급1회분과 방 결과의 확률/보상2회분은 유지하며, 비밀의 방 보상도8 상한으로 제한한다. V8은 DB 보유량 제약만0~8로 바꾸며 기존 양을 채우거나 삭제하지 않는다.

SE-06 세척은1시간·세척액1회분, 체력0이다. 체력0에서도 가능하며 INSUFFICIENT_STAMINA는 세척의 제한 사유가 아니다. 세척 가능 상태/남은 수명/세척액·게임 종료/기한 규칙과 선택 묶음 만료 보호는 유지한다. GameScreen.clean.staminaCost=0이며 projected와 실제 실행의 체력이 일치한다. 정상/최종 결과 조회 응답의 구조와API19개는 유지했다.

SE-01~17 예시와 OpenAPI 정상 예시는 현재 서버에서 다시 수집했다. 추가 경계/재시도 파일은 변경 전의 역사적 기록일 수 있으므로 현재 수치는 이 절과 최신 screen을 따른다.


### SE-18·SE-19. 선택 묶음 일괄 세척·폐기 (2026-10-28)

- SE-18: `POST /api/games/{id}/items/clean`
- SE-19: `DELETE /api/games/{id}/items` (JSON 본문 필요)
- 공통 요청: `{"batchIds":[123,124]}`. 필수 배열, 1~20개 양의 정수, 중복 금지. 휴대품·상자를 함께 선택할 수 있다.
- 정상200 ActionResult. 선택 Idempotency-Key/Idempotency-Replayed 계약을 동일하게 적용한다. 병합·폐기로 ID가 없어져도 같은 요청 번호의 재전송은 최초 응답을 반환한다.
- 오류400 INVALID_REQUEST: 빈 배열/누락/null/범위/중복. 404 NOT_FOUND: 다른 판 또는 없는 묶음. 409 GAME_ENDED/LEGACY_GAME: 공통 변경 제한.
- 세척: 선택 묶음 수가 세척액보다 많으면409 INSUFFICIENT_CLEANER. 하나라도 CLEAN/UNUSABLE/이미48h 만료이면409 NOT_CLEANABLE. **어떤 재료나 세척액도 변경하지 않는다.** 모든 선택을 시작 시 검사하고 모두 CLEAN으로 바꾼 뒤 같은 재료·저장소를 최대6개씩 합친다. 묶음당 시간1h·액1·체력0. 선택 재료는 처리 중 만료되지 않으며, 선택 외 미세척 재료는 전체 소요 시간만큼 만료될 수 있다. 생존/질병 기한 도달은 기존 규칙대로200 FAILED 결과.
- 세척 DB: item_batch UPDATE(CLEAN/수량), 병합으로 비워진 행 DELETE; game.cleaned에 실제 재료 수량 합산, cleaner_amount는 선택 묶음 수만큼 감소, elapsed_hours 증가, finish에서 만료·엔딩 처리.
- 폐기: 상태와 저장소에 관계없이 선택한 행을 모두 DELETE, game.discarded에 실제 재료 수량 합산. 시간·체력·세척액 비용 없음.
- 두 작업 모두 게임 잠금과 한 트랜잭션으로 처리한다. 선택 검증·SQL 처리 도중 오류 시 api_request를 포함해 전체 롤백.
- 프론트: 선택 재료를 한 번 더 눌러 해제. 세척액 부족은 버튼 비활성+사유. 폐기는 전체 이름·수량 확인 후 요청, 취소는 서버 변경 없음. 처리 뒤 저장소 창 유지. 휴식은 체력 소모0 문구를 생략하고 실제 회복량·소요 시간만 표시한다.
- 실제 응답: [SE-18 일괄 세척](examples/SE-18.json), [SE-19 일괄 폐기](examples/SE-19.json). 기존 한 묶음 API는 유지한다.

# Scrap Escape 프론트엔드 개발 명세서 — 팀원 담당

담당: **B — 팀원** · 기준일:2026-10-27 · 분업 계약v1.3 · 공통 계약 SHA-256 앞16자:`88058b6edd2c0434`

이 파일 하나에 현재 백엔드 현황·실행법·공통 인터페이스·본인 작업 범위·완료 기준·AI 전달 프롬프트가 포함되어 있다. 저장소와 기획안에 접근할 수 있는 담당자는 다른 담당자 명세서를 받지 않아도 자기 작업을 시작할 수 있다.

## 공통 계약 v1.3 — 양쪽 문서에서 동일하게 유지

이 절은 두 담당자에게 동일한 내용이다. 변경이 필요하면 A(본인)가 두 문서를 함께 수정하고 B(팀원)와 확인한다. 기획의 과거 수치와 이 문서가 다르면 **현재 저장소의 API 구현·OpenAPI를 대조한 뒤 차이를 공유**한다. 임의로 서버/다른 담당자의 계약을 변경하지 않는다. 이 문서의 화면 모듈·파일·Context는 앞으로 구현할 프론트 계약이며, 기존 구현이라고 오해하지 않는다.

### C1. 목적·기준·준비물

- 게임: **Scrap Escape**. 오염된 도시에서 탐색→재료 보관·세척→부품 제작/장착→비행기 탈출. 숲의 추락 비행기가 안전지대이며 도시 건물들이 탐색지다.
- 저장소: https://github.com/CallSuu/ScrapEscape . 백엔드 확인 기준 커밋: **e339210**, 기준 브랜치 `backend/bootstrap`. 새 계약 문서를 전달하는 커밋은 이 커밋의 후손이어야 한다.
- 역할 A=본인(앱 통합·메인/HUD·탐색·비밀의 방), 역할 B=팀원(안전지대·결과). 이 두 명세서가 정하는 공통 계약이 같아야 한다.
- 필요한 전달물: 이 담당 명세서 + 게임 기획안 + 저장소 읽기 권한/클론. 문서만으로 백엔드 파일이 생성되는 것은 아니다. 첨부 이미지는 배치 참고이며 복제 대상이 아니다.
- 기준일:2026-10-27. 현재 A의 시작·메인/HUD·탐색 선택·탐색 보상·비밀의 방 선택/결과 화면을 구현했다. MySQL115개·JS46개 통과. B의 안전지대·최종 결과 제품 모듈과 두 브랜치 최종 통합은 아직 별도 작업이다.
- Java17, Spring Boot3.5.16, Gradle Wrapper8.14.3, MySQL, IntelliJ IDEA. 프론트는 **HTML/CSS/순수 JavaScript ES modules(.mjs)**, 같은 Spring Boot 서버에서 제공한다. React/Vue/Vite/TypeScript 컴파일·npm 빌드·새 백엔드·별도 프론트 포트는 도입하지 않는다. 아래 타입 표기는 문서용이며 실행 파일은 JavaScript다.
- 화면 목표: 데스크톱 가로 **1440×900** 기본, **1280×720·1920×1080**에서도 버튼·HUD가 겹치지 않음. 모바일 전용 UI는 이번 범위 제외.

### C2. 현재 백엔드 구현 완료 범위

| 기능 | 현재 구현 | 관련 API·근거 |
|---|---|---|
| 새 게임·판 분리 | UUID, 초기 상태, 게임별 데이터 분리, 계정 없이 한 판 진행 | SE-01·02, GameService.create/state |
| 장소·재료 확대 | 장소6곳·재료10종, 장소-재료 연결 테이블 | SE-03·04, V2 |
| 일반 탐색 | 낮0~3·밤0~4개,0개5%, 최대2종, 빈손 문구, 초과 수량 안내 | SE-04, GameRules.loot |
| 보상 후보 확률 | 동일 상대 가중치1, 총수량2이상이면2종으로 나누고 첫 선택 재료를 우선 저장 | SE-03·04 |
| 체력·시간·낮/밤 | 행동 기반 시간, 낮6h/체력2·밤7h/3, 출발 시각 판정 | SE-03·04·09 |
| 인벤토리·보관함 |4칸·16칸, 묶음 최대6, 전량 이동·공간 부족 차단 | SE-05, V3·V4 |
| 세척·합치기 |한 묶음1h/체력1/액1, CLEAN을6개씩 자동 정리, DIRTY 획득 시각 분리 | SE-06 및 이동 내부 처리 |
| 재료 만료·폐기 |DIRTY48시간 후 UNUSABLE, 자동 삭제 없음, 직접 전체 폐기 | SE-07 및 finish |
| 휴식 |1~8h,6h 이상 체력+2보너스,4h 이상 오염−10·연속 초기화 | SE-09 |
| 오염·연속 탐색 |0~100, 탐색+10·연속3회째부터+5 | SE-04 및 상태 |
| 질병 |0~59:0%,60~79:30%,80~89:40%,90~100:60%, 탐색시간·체력+2, 중첩 없음 | SE-04·15, GameRules |
| 병원·치료 |병원 키트1확정, 별도 최대2, 치료1h·오염−60·질병 제거, 예방 사용 가능 | SE-04·10 |
| 질병 방치 실패 |발병 후72시간 기한, 휴식으로 초기화하지 않음, 치료 완료 기한 우선 | SE-02·09·10·15 |
| 방사능 폭풍 |시작 간격24~48h·지속1~3h, 실제 노출시간×5, 안전지대 면제 | 새 게임·탐색·방 내부 처리 |
| 조합·수리 |고정7종 중 무작위4개, 양쪽 저장소 CLEAN 합산,3h 즉시 장착 | SE-08 |
| 탈출·전체 제한 |경과240시간 실패,4개 수리 후 별도 탈출 선택, 실패가 성공보다 우선 | SE-11·finish. 두 사유의 선후관계는 GAP-01 |
| 열쇠·방 발견 |일반 탐색 열쇠10%·해당 방15% 독립, 최대 열쇠2, 방 위치 기억 | SE-04·12·13 |
| 방 입장·재방문 |즉시1h/체력1, 재방문 낮4h/2·밤5h/3·질병+2, 키1, 장소당1회 | SE-15 |
| 방 창 닫기 |defer로 시간·자원 없이 기억, 다음 탐색 가능, 즉시 기회 종료 | SE-14 |
| 방 결과 |좋음80%·나쁨20%, 보상 새 획득 시각, 미세척 인벤토리만 손실 | SE-15 |
| 기본·확장 통계 |획득·세척·폐기·탐색·수리·질병·폭풍, 방·열쇠·손실 통계 | State.statistics·secrets.statistics |
| 화면에 줄 상태 정보 |체력·오염·질병 기한·보급품·폭풍·슬롯·만료 잔여 시간, ? 표시·입장 제한 | SE-02·03·13. 실제 UI는 별도 |
| 무결성·오류 처리 |게임 행 잠금, 트랜잭션, 외래 키·수량 제약, 공통 오류 JSON | 각 변경 API·V1~V6 |
| 재전송 중복 방지 |UUID 요청 번호·원래 응답 저장·동시/재시작/종료 후 재사용 | 변경 API11개·V6·14개 회귀 테스트 |
| 화면 조회·안전지대 미리보기 |읽기 전용 스냅샷·실행 제한/예상 상태·만료 경고 | SE-16·신규11개 통합 테스트 |
| 기존 DB 보존 |Flyway, 기존 조합·이름 보존,6개 초과 묶음 분할, 이전 규칙 판 조회 | 마이그레이션 테스트·LEGACY_GAME |


추가로 이미 있는 공통 프론트 기반: `/js/game-api.mjs`의 `createGameClient`, `GameApiError`, `applyActionAvailability`. 나머지 HTML/배경/HUD/창은 아직 없다. 첫 실행 기준 `/`가404여도 백엔드 실패가 아니다.

범위 밖/보류: 로그인·이어하기·실제 초 타이머·전투·판매, 종료 판 삭제/자동정리, 최종 요약 별도 저장·순위표, 실제 탈출률/플레이 길이 조율. 결과 조회 자체는 State에 구현되어 있으므로 결과 화면을 만들 수 있다. 비밀의 방은 초기 기획상 확장이었지만 현재 백엔드는 완료되어 **A의 프론트 담당 범위에 포함**한다.

#### 화면에서 틀리기 쉬운 확정 규칙

1. 시작1일08:00·체력10·세척액1/5·키트0/2·열쇠0/2. 경과240h 생존 제한. `day==10` 또는 날짜 자정으로 실패를 직접 판정하지 않는다. 상태/남은 시간은 응답 기준.
2. 인벤토리4칸·상자16칸·묶음6개. 같은 재료라도 DIRTY는 획득 시각이 다르면 별도 묶음이다. 슬롯은 종류 수나 총수량이 아니라 배열 길이. 세척액/키트/열쇠는 별도 수치로 슬롯을 쓰지 않는다.
3. 낮 탐색6h/체력2, 밤(20:00~06:00)7h/3. 출발 시각 기준. 질병이면 탐색/재방문에2h·체력2 추가. 병이 있어도 탐색 가능하다.
4. 일반 탐색 총수량은 낮0/1/2/3=5/15/35/45%, 밤0/2/3/4=5/20/35/40%. **최대4개는 총수량이며4종류라는 뜻이 아니다.** 최대2종, 빈손도 정상 행동이다. 공간에 못 담은 것은 leftBehindQuantity로 보고된다.
5. 일반 탐색 세척액50%·1회분(통 상한5). 병원은 키트1개 확정(상한2). 가득 차면 실제 추가0일 수 있다. 키트·세척액 발견과 실제 추가량을 구분한다.
6. DIRTY48h 후 UNUSABLE, CLEAN은 만료 없음. 세척은 묶음 전체1h/체력1/액1. 소멸 직전 시작한 세척 대상은 보호되고 다른 묶음은 만료될 수 있다. 폐기는 묶음 전체이며 되돌리기 API 없음.
7. 휴식1~8h, 체력=시간+(6h 이상이면2), 상한10.4h 이상이면 오염−10/연속 탐색 초기화. 실제 회복량은 projected−현재. 휴식으로 질병 치료/기한 초기화 안 됨.
8. 오염 기본+10·연속3회째부터+5, 폭풍 실제 노출1h당+5. 폭풍 시작 간격24~48h·지속1~3h. 안전지대 행동은 폭풍 오염 없음. 화면에서 난수를 생성하지 않는다.
9. 발병 확률은 오염0~59=0%,60~79=30%,80~89=40%,90~100=60%. 발병72h 내 치료. 치료1h·키트1·오염−60·질병 제거. 완료가 기한 이상이면 실패하며 치료/키트 소비가 성립하지 않는다. 둘 다 기한 초과면 TIME_LIMIT 우선 유지.
10.7종 조합 중 서버가 고른4개만 수리. 제작/장착3h, 양쪽 보관함 CLEAN 재료 합산, 완성품 슬롯 없음.4개 수리해도 자동 성공 아님: 탈출 POST를 선택해야 SUCCESS.
11. 열쇠10%·해당 탐색지 방 발견15%, 독립 판정. 장소당 방1개·총6개. 미발견은 숨김, 발견 버튼은 **`?`만**. 개방 여부/열쇠 수는 서버 값. 열쇠 있어도 자동 입장 금지.
12. 즉시 입장1h/체력1/키1. 재방문 낮4h/2·밤5h/3(+질병2씩). 정상 탐색보다2h 짧다. 재방문은 일반 탐색 보상을 중복 지급하지 않는다. 방은 한 번만 개방.
13. 방 결과: 재료3~4개50%, 보급품30%(세척액2/키트1 각15%), 미세척 휴대재료1~2개 손실10%, 오염+15 10%. 손실할 재료 없으면 오염으로 대체. 상자·CLEAN·UNUSABLE은 손실에서 보호. 공급 상한/빈칸 때문에 실제 보상0일 수 있음.
14. 성공한 다른 행동/방 지나치기는 즉시 입장 기회를 끝낸다. GET/실패 요청은 소모하지 않는다. 키를 못 얻어도 위치 기억·재방문 선택 가능.
15. 기한 위험은 **경고이고 실행 허용**. 자원 부족·종료 상태는 실행 비활성. 클라이언트는 비용·수량·만료·성공을 임의로 반영하지 않고 새 screen으로 그린다.

### C3. API16개와 데이터 계약

기본 주소 `http://localhost:8080`. `{id}`는 동적으로 받은 게임 UUID, 나머지ID는 응답의 정수다. 고정 게임UUID·batchId 금지. 본문 있는 요청만 JSON. DTO 필드는 필수이며 null 불가. 없는 본문에는 `{}`를 임의로 넣지 않아도 된다.

| ID | 요청 | 본문 | 성공 응답 |
|---|---|---|---|
| SE-01 | `POST /api/games` | 없음 | 201 State |
| SE-02 | `GET /api/games/{id}` | 없음 | 200 State |
| SE-03 | `GET /api/games/{id}/locations` | 없음 | 200 LocationCard[] |
| SE-04 | `POST /api/games/{id}/explorations` | `{locationId:number}` | 200 ActionResult |
| SE-05 | `PATCH /api/games/{id}/items/{batchId}/storage` | `{storage:"INVENTORY"\|"CHEST"}` | 200 ActionResult |
| SE-06 | `POST /api/games/{id}/items/{batchId}/clean` | 없음 | 200 ActionResult |
| SE-07 | `DELETE /api/games/{id}/items/{batchId}` | 없음 | 200 ActionResult |
| SE-08 | `POST /api/games/{id}/repairs/{partId}` | 없음 | 200 ActionResult |
| SE-09 | `POST /api/games/{id}/rests` | `{hours:1..8}` | 200 ActionResult |
| SE-10 | `POST /api/games/{id}/treatments` | 없음 | 200 ActionResult |
| SE-11 | `POST /api/games/{id}/escape` | 없음 | 200 ActionResult |
| SE-12 | `GET /api/games/{id}/secret-rooms` | 없음 | 200 SecretRoomCard[] |
| SE-13 | `GET /api/games/{id}/secret-rooms/{locationId}` | 없음 | 200 SecretRoomCard |
| SE-14 | `POST /api/games/{id}/secret-rooms/{locationId}/defer` | 없음 | 200 ActionResult |
| SE-15 | `POST /api/games/{id}/secret-rooms/{locationId}/open` | `{mode:"IMMEDIATE"\|"REVISIT"}` | 200 ActionResult |
| SE-16 | `GET /api/games/{id}/screen` | 없음 | 200 GameScreen |


명세 원문: [전체 API](../api/Scrap_Escape_API_명세서.md) · [OpenAPI](../api/openapi.json) · [실제 screen 예시](../api/examples/SE-16.json) · [기한 경계](../api/examples/screen-deadline.json) · [종료 판](../api/examples/screen-ended.json). 예시의 게임ID는 검증 후 삭제됐으므로 API에 재사용하지 않는다.

주 화면은 SE-16 하나로 갱신한다. `GameScreen={state,locations,actions}`. `actions.rests`는 배열이며 hours로 찾고, `actions.items`는 batchId, `actions.repairs`는 partId로 찾는다. 배열 인덱스를 ID로 취급하지 않는다. `state.repairs`는 선택된4개만 담기며 전체7개 목록 API는 없다.

```typescript
type UUID = string;
type Storage = "INVENTORY" | "CHEST";
type Condition = "DIRTY" | "CLEAN" | "UNUSABLE";
type Status = "RUNNING" | "SUCCESS" | "FAILED";
type RoomMode = "IMMEDIATE" | "REVISIT";
type RoomOutcome = "MATERIALS" | "CLEANER" | "MEDICAL_KIT" | "MATERIAL_LOSS" | "CONTAMINATION";
interface OpenRoomRequest {
  mode: RoomMode;
}
interface SecretFindings {
  keyFound: boolean;
  keysAdded: number;
  roomDiscovered: boolean;
  roomLocationId: number | null;
}
interface SecretStatistics {
  discoveredRooms: number;
  openedRooms: number;
  unopenedRooms: number;
  keysAcquired: number;
  keysUsed: number;
  goodOutcomes: number;
  badOutcomes: number;
  materialsCollected: number;
  materialsLost: number;
}
interface SecretState {
  keyAmount: number;
  keyCapacity: number;
  keyProbabilityPercent: number;
  discoveryProbabilityPercent: number;
  rooms: SecretRoomCard[];
  statistics: SecretStatistics;
}
interface RoomVisitPreview {
  allowed: boolean;
  unavailableReason: string | null;
  durationHours: number;
  staminaCost: number;
  keyCost: number;
  baseContamination: number;
  stormExposureHours: number;
  stormContamination: number;
  expectedContaminationIncrease: number;
  possibleExtraContamination: number;
  rewardFreeSlots: number;
  deadlineRisk: boolean;
}
interface SecretRoomCard {
  locationId: number;
  locationName: string;
  buttonLabel: string;
  discoveredHour: number;
  opened: boolean;
  immediateAvailable: boolean;
  immediate: RoomVisitPreview;
  revisit: RoomVisitPreview;
  openedHour: number | null;
  lastVisit: SecretRoomOutcome | null;
}
interface SecretRoomOutcome {
  locationId: number;
  mode: RoomMode;
  outcome: RoomOutcome;
  durationHours: number;
  staminaCost: number;
  contaminationIncrease: number;
  stormExposureHours: number;
  loot: Loot;
  lostQuantity: number;
}
interface ExploreRequest {
  locationId: number;
}
interface MoveRequest {
  storage: Storage;
}
interface RestRequest {
  hours: number;
}
interface Item {
  batchId: number;
  materialId: number;
  name: string;
  quantity: number;
  storage: Storage;
  acquiredHour: number;
  condition: Condition;
  expiresInHours: number | null;
}
interface Ingredient {
  materialId: number;
  name: string;
  requiredQuantity: number;
  availableQuantity: number;
}
interface Repair {
  partId: number;
  name: string;
  repaired: boolean;
  canCraft: boolean;
  ingredients: Ingredient[];
}
interface Statistics {
  explorations: number;
  dayExplorations: number;
  nightExplorations: number;
  emptyExplorations: number;
  collected: number;
  cleaned: number;
  discarded: number;
  repairedParts: number;
  diseaseCount: number;
  treatmentCount: number;
  medicalKitsUsed: number;
  maxContamination: number;
  stormExposureHours: number;
}
interface Storm {
  startHour: number;
  endHour: number;
}
interface ActionAvailability {
  allowed: boolean;
  unavailableReason: string | null;
  durationHours: number;
  staminaCost: number;
}
interface State {
  gameId: UUID;
  status: Status;
  elapsedHours: number;
  day: number;
  hour: number;
  remainingHours: number;
  night: boolean;
  stamina: number;
  maxStamina: number;
  cleanerAmount: number;
  cleanerCapacity: number;
  inventoryCapacity: number;
  chestCapacity: number;
  inventory: Item[];
  chest: Item[];
  repairs: Repair[];
  statistics: Statistics;
  stackLimit: number;
  medicalKitAmount: number;
  medicalKitCapacity: number;
  contamination: number;
  diseased: boolean;
  treatmentRemainingHours: number | null;
  consecutiveExplorations: number;
  currentStorm: Storm | null;
  nextStorm: Storm | null;
  treatment: ActionAvailability;
  escape: ActionAvailability;
  failureReason: string | null;
  rulesVersion: number;
  secrets: SecretState;
}
interface ActionResult {
  message: string;
  state: State;
  loot: Loot | null;
  secretFindings: SecretFindings | null;
  secretRoom: SecretRoomOutcome | null;
}
interface MaterialLoot {
  materialId: number;
  name: string;
  quantity: number;
  collectedQuantity: number;
}
interface Loot {
  quantity: number;
  collectedQuantity: number;
  leftBehindQuantity: number;
  materials: MaterialLoot[];
  cleanerFound: boolean;
  cleanerAdded: number;
  medicalKitsAdded: number;
}
interface Candidate {
  materialId: number;
  name: string;
  weight: number;
}
interface RewardProbability {
  quantity: number;
  probabilityPercent: number;
}
interface LocationCard {
  locationId: number;
  name: string;
  description: string;
  materials: Candidate[];
  durationHours: number;
  staminaCost: number;
  rewards: RewardProbability[];
  cleanerProbabilityPercent: number;
  canExplore: boolean;
  unavailableReason: string | null;
  medicalKitReward: number;
  baseContamination: number;
  consecutiveContamination: number;
  stormExposureHours: number;
  stormContamination: number;
  expectedContaminationIncrease: number;
  diseaseProbabilityAfterExploration: number;
  secretRoom: SecretRoomCard | null;
}
interface ActionProjection {
  elapsedHours: number;
  stamina: number;
  contamination: number;
  cleanerAmount: number;
  medicalKitAmount: number;
  diseased: boolean;
  status: Status;
  failureReason: string | null;
  expiringBatchIds: number[];
}
interface SafeActionPreview {
  allowed: boolean;
  unavailableCode: string | null;
  unavailableReason: string | null;
  durationHours: number;
  staminaCost: number;
  projected: ActionProjection | null;
}
interface RestPreview {
  hours: number;
  preview: SafeActionPreview;
}
interface ItemActionPreview {
  batchId: number;
  moveTo: Storage;
  move: SafeActionPreview;
  clean: SafeActionPreview;
  discard: SafeActionPreview;
}
interface RepairActionPreview {
  partId: number;
  preview: SafeActionPreview;
}
interface SafeActions {
  rests: RestPreview[];
  items: ItemActionPreview[];
  repairs: RepairActionPreview[];
  treatment: SafeActionPreview;
  escape: SafeActionPreview;
}
interface GameScreen {
  state: State;
  locations: LocationCard[];
  actions: SafeActions;
}

```

Java `GameModels`/OpenAPI에서 확인한 현재 DTO 목록이다. null은 숨기거나 ‘없음’으로 표시하고0과 구분한다. `RoomVisitPreview`는 SafeActionPreview와 다른 구조이며 projected/unavailableCode가 없다. `LocationCard`는 allowed가 아니라 canExplore다. `ActionResult.loot/secretFindings/secretRoom`은 일반 행동에서 null일 수 있다.

#### 오류·재전송

- 공통 오류 `{code,message}`. 대표400 INVALID_REQUEST/INVALID_IDEMPOTENCY_KEY,404 NOT_FOUND,409 GAME_ENDED/LEGACY_GAME/자원·공간 부족/ROOM_ALREADY_OPENED/IMMEDIATE_UNAVAILABLE/IDEMPOTENCY_KEY_REUSED,500 INTERNAL_ERROR. 모든 code에 기본 표시 경로를 둔다.500/네트워크 문제도 단순한 사용자 잘못으로 표시하지 않는다.
- HTTP200+state.status=FAILED는 정상 처리된 실패 엔딩이다. HTTP201은 생성 State, 변경200은 ActionResult, GET screen200은 GameScreen이다. 셋을 혼동하지 않는다.
- 서버는 선택 Idempotency-Key를 지원하고 공통 모듈이 UUID를 관리한다. 같은 키/경로/검증DTO는 원래 응답 재사용, 다른 요청에 같은 키면409. 원래 응답은 과거 상태일 수 있으므로 모듈은 항상 screen을 재조회한다.
- 하나의 페이지에 createGameClient는 **정확히1개**, A의 app.mjs만 생성. B나 개별 View는 별도 클라이언트/fetch/axios/UUID/재시도 루프를 만들지 않는다.
- busy=true면 실행 버튼 모두 비활성. hasPending=true면 새 행동·새 게임·임의refresh 금지, 재시도만 `!busy`일 때 허용. 정보와 X는 유지한다.
- 연결 끊김/15초 제한/500/응답 파싱 실패: 사용자가 재시도할 때 같은 키·본문. 이미 행동 성공, screen만 실패했으면 GET만 재시도한다. 자동 반복 없음.
-409 뒤 가능한 경우 모듈이 screen 갱신. 갱신 실패 시 원래 오류 유지. 다음 정상 창 진입 때 refresh한다. 페이지 새로고침 후 요청 번호 복원/이어하기는 범위 밖.

### C4. 파일 소유권 — 수정 충돌 방지

아래는 **앞으로 만들 파일**이며 기준 커밋에서 게임 화면 파일은 없다. 기존 공통 모듈 외에 없는 경로는 각 담당자가 생성한다. 모든 경로는 저장소 루트 기준이다.

| 소유자 | 수정·생성 허용 경로 |
|---|---|
| A | `src/main/resources/static/index.html` |
| A | `src/main/resources/static/css/common.css`, `main.css`, `exploration.css`, `exploration-result.css`, `secret-room.css` |
| A | `src/main/resources/static/js/app.mjs`, `main.mjs`, `exploration.mjs`, `exploration-result.mjs`, `secret-room.mjs` |
| A | `src/main/resources/static/assets/main/**`, `assets/locations/**` |
| A | `src/test/javascript/main.test.mjs`, `exploration.test.mjs`, `exploration-result.test.mjs`, `secret-room.test.mjs`, `app.test.mjs`（필요한 검증에 한함） |
| B | `src/main/resources/static/css/safe-zone.css`, `result.css` |
| B | `src/main/resources/static/js/safe-zone.mjs`, `result.mjs` |
| B | `src/main/resources/static/assets/safe-zone/**`, `assets/result/**` |
| B | `src/main/resources/static/dev/safe-zone.html`, `dev/safe-zone-preview.mjs`（독립 확인용） |
| B | `src/test/javascript/safe-zone.test.mjs`, `result.test.mjs`（필요한 검증에 한함） |
| 공동 계약·기본 동결 | 기존 `static/js/game-api.mjs`, `docs/api/**`, Java·SQL·Gradle·application 설정·AGENTS.md |

B는 index.html/app.mjs/common.css를 편집하지 않는다. A가 다음 공통/A 파일을 index.html에서 가져오고 공통 Context를 준비한다. B의 CSS는 해당 모듈 import가 성공했을 때 A의 host가 한 번만 로드한다. B의 개발용 HTML은 운영 index에서 import하지 않는다. 계약 변경이 필요하면 작업 전에 A에게 요청한다. 소유권 밖 파일을 AI가 편의상 바꾸도록 허용하지 않는다.

```html
<link rel="stylesheet" href="/css/common.css">
<link rel="stylesheet" href="/css/main.css">
<link rel="stylesheet" href="/css/exploration.css">
<link rel="stylesheet" href="/css/secret-room.css">
<script type="module" src="/js/app.mjs"></script>
```

최종 통합 때 host는 `/css/safe-zone.css`, `/css/result.css`를 모두 정상 제공·적용해야 한다. B 파일이 없는 A의 단독 중간 개발에서만 B 모듈 부재에 대한 준비 중 안내를 허용하며, 최종 완료 기준에서는 해당404도 없어야 한다.

### C5. 모듈 export·Context — 합칠 때 바꾸지 않는 인터페이스

```typescript
type Flags = { busy: boolean; hasPending: boolean };
type Panel = null | { name:"safe-zone" } | { name:"exploration" }
  | { name:"secret-room"; locationId:number; fromDiscovery?:boolean }
  | { name:"exploration-result"; locationId:number; activity:"EXPLORE"|"OPEN_ROOM"; receipt:ActionResult }
  | { name:"result" };
type Command =
  | {type:"START_GAME"}
  | {type:"EXPLORE"; locationId:number}
  | {type:"MOVE_ITEM"; batchId:number; storage:Storage}
  | {type:"CLEAN_ITEM"; batchId:number}
  | {type:"DISCARD_ITEM"; batchId:number}
  | {type:"REPAIR_PART"; partId:number}
  | {type:"REST"; hours:number}
  | {type:"TREAT"}
  | {type:"ESCAPE"}
  | {type:"DEFER_ROOM"; locationId:number}
  | {type:"OPEN_ROOM"; locationId:number; mode:RoomMode}
  | {type:"RETRY"}
  | {type:"REFRESH"}
  | {type:"ACK_EXPLORATION_RESULT"};
type DispatchResult =
  | {ok:true; screen:GameScreen | null; result:State | ActionResult | null}
  | {ok:false; error:{code:string; message:string; retryable:boolean}};
interface Context {
  getScreen(): GameScreen | null;
  getFlags(): Flags;
  getPanel(): Panel;
  dispatch(command:Command): Promise<DispatchResult>;
  navigate(panel:Panel): Promise<void>;
  announce(message:string): void;
}
interface View {
  render(screen:GameScreen|null, flags:Flags): void;
  destroy(): void;
}
// root: HTMLElement, context: 위 Context. 실제 구현 파일은 .mjs.
```

실제 구현은 `export function create…View(root, context) { return {render,destroy}; }` 형식. 정해진 export:

| 파일 | 이름 있는 export | mount 위치 |
|---|---|---|
| A `js/main.mjs` | `createMainView` | `#se-main`（상시） |
| A `js/exploration.mjs` | `createExplorationView` | `#se-exploration-content`（전체 탐색 화면 활성 중） |
| A `js/exploration-result.mjs` | `createExplorationResultView` | `#se-exploration-content`. receipt는 context.getPanel()에서 읽음 |
| A `js/secret-room.mjs` | `createSecretRoomView` | `#se-exploration-content`. locationId는 context.getPanel()에서 읽음 |
| B `js/safe-zone.mjs` | `createSafeZoneView` | `#se-panel-content` |
| B `js/result.mjs` | `createResultView` | `#se-panel-content` |

- root는 **이미 존재하는 HTMLElement**로 전달한다. View는 자기 root 하위만 생성/변경한다. 다른 View import, window 전역 상태, 외부 DOM querySelector, 다른 담당자의 DOM ID 접근 금지. document.createElement는 허용한다.
- render는 화면을 그리는 동기 함수다. fetch/dispatch/navigate/게임 상태 수정 금지. 선택 탭·휴식 시간·폐기 확인 같은 **화면 로컬 상태**는 View 클로저에 보관한다. 서버 snapshot 객체는 불변처럼 다룬다.
- 이벤트에서 context.dispatch/navigate를 호출한다. dispatch는 성공/오류를 위 union으로 반환하며 **네트워크/게임 오류로 Promise를 reject하지 않는다**. 호스트가 기존 client의 rejection을 catch·표시하고 변환해야 한다. 오류를 무시한 성공 애니메이션 금지.
- DispatchResult.result는 표시 메시지/발견 안내용, HUD의 근거는 항상 screen이다. REFRESH에서는 result=null. START_GAME은 시작 화면 복귀 시 screen/result=null, 실제 생성 시 result=State, 나머지 변경은 ActionResult. RETRY는 원래 행동의 result 타입을 유지한다.
- context.navigate는 일반 오류를 전역 표시하고 resolve한다. navigate(null)은 닫기. navigate 자체로 재료·체력·시간을 바꾸지 않으며, 즉시 방 닫기 예외는 다음 절에 명시한다.
- destroy는 자기 이벤트 listener/Observer/타이머·로컬 DOM을 정리한다. 네트워크 요청/게임 종료 API 없음. root에 이벤트 위임하거나 AbortController로 listener 정리. render가 반복돼도 listener를 중복 등록하지 않는다.
- B 소유 제품 경로에 A가 임시 stub를 생성하지 않는다. 모듈 부재는 A의 동적 import 실패 처리와 준비 중 안내로 대응한다. 테스트 전용 더미 Context/View는 A 소유 테스트 파일에만 둘 수 있다. 최종 통합은 실제 B 모듈로 검증한다.

#### app.mjs의 command → 기존 client 매핑

`base = "/api/games/" + latestScreen.state.gameId`로 만들고, START_GAME 전에는 gameId가 없어도 된다.

| Command | 호출 |
|---|---|
| START_GAME | 현재 screen이 있으면 로컬 시작 화면 복귀(screen/result=null), 없으면 `client.act("POST","/api/games")` |
| EXPLORE | `client.act("POST",base+"/explorations",{locationId})` |
| MOVE_ITEM | `client.act("PATCH",base+"/items/"+batchId+"/storage",{storage})` |
| CLEAN_ITEM | `client.act("POST",base+"/items/"+batchId+"/clean")` |
| DISCARD_ITEM | `client.act("DELETE",base+"/items/"+batchId)` |
| REPAIR_PART | `client.act("POST",base+"/repairs/"+partId)` |
| REST | `client.act("POST",base+"/rests",{hours})` |
| TREAT | `client.act("POST",base+"/treatments")` |
| ESCAPE | `client.act("POST",base+"/escape")` |
| DEFER_ROOM | `client.act("POST",base+"/secret-rooms/"+locationId+"/defer")` |
| OPEN_ROOM | `client.act("POST",base+"/secret-rooms/"+locationId+"/open",{mode})` |
| RETRY | `client.retry()` |
| REFRESH | `client.refresh()`; normalize to `{ok:true,screen,result:null}` |
| ACK_EXPLORATION_RESULT | 로컬 확인. HTTP 없음. 정상은 메인, 새 방 발견이면 방 선택, 종료 상태이면 B 최종 결과로 이동 |

호스트 흐름 고정:

1. 앱 로드 시 자동 POST 금지. 새 게임 시작 버튼만 표시한다. 버튼/START_GAME 성공 시 latestScreen 설정. START_GAME 재시도도 기존 client의 pending을 사용한다.
2. onSnapshot에서 latestScreen 갱신→mainView와 열린 View render. onBusy에서도 flags 갱신→같은 View 재-render. **onSnapshot은 busy=true 중에도 먼저 호출될 수 있다.** 다음 onBusy(false) 전에는 실행 버튼을 활성화하지 않는다.
3. 성공 상태가 SUCCESS/FAILED이면 호스트가 기존 창 destroy 후 result 창을 연다. 단 EXPLORE/OPEN_ROOM은 먼저 exploration-result에서 행동 보상을 보여주고 확인 후 최종 result로 이동한다. View가 자기 render 안에서 결과 창을 열지 않는다. terminal 상태가 같다고 매번 result 창을 다시 열지 말고, 게임별 RUNNING→terminal 전환(또는 처음 읽은 terminal)에만 자동으로 한 번 연다. 닫으면 ‘결과 보기’로 다시 연다.
4. 호스트는 busy와hasPending으로 모든 변경을 막고 별도의 재시도 버튼을 관리한다. RETRY만 `hasPending&&!busy` 때 허용. 일반 REFRESH도 pending 중 금지. START_GAME 재시도 성공 후 초기화 역시 원래 START_GAME 완료로 처리한다.
5. 기존 client의 모든 오류를 호스트가 catch. onError 알림과 catch 알림을 중복 표시하지 않는다. 사전 검증에서 발생하는 REQUEST_BUSY/PENDING_REQUEST 등 onError에 안 오는 오류도 catch에서 처리한다. 사용한 오류 객체/요청 단위로 이미 표시했는지 관리한다.
6. announce는 공통 메시지/로그 영역에 textContent로 표시한다. HTML 문자열 삽입 금지. 마지막 행동 메시지와 overflow·발병 안내를 보여준다. 즉시 방 발견 시 안내와 ? 버튼을 제공하고, **방을 자동 개방하거나 사용자 의사 없이 열쇠를 소비하지 않는다.** 탐색 보상을 먼저 보여주고 확인 시 새 발견이면 비밀의 방 선택 전체 화면으로 이동한다.
7. 비동기 창 전환은 로컬 navigation 세대 번호 등으로 취소된 전환을 구분한다. X/다른 창 선택 후 이전 REFRESH가 완료돼도 과거 창을 다시 열지 않는다. 정상 창 진입은 idle·pending없음이면 REFRESH 후 snapshot에 근거해 연다. 조회 실패면 현재 창을 유지하고 오류 표시. busy/pending 중에는 네트워크를 끼워 보내지 않고 현재 snapshot으로 읽기 전용 창을 열 수 있다.
8. 메인/결과의 새 게임은 START_GAME을 통해 로컬 시작 화면으로 돌아간다. RUNNING 중이면 먼저 확인한다. 이때 screen=null·열린 창/선택/결과 표시·메시지/밤 테마를 비우고 API는 보내지 않는다. 시작 화면의 새 게임 시작에서 START_GAME을 다시 선택해야 POST로 새 UUID를 생성한다. busy/pending 중 복귀는 막는다. 시작 화면에서 REFRESH는 NO_GAME으로 차단해 이전 판을 복원하지 않는다. 기존 게임 삭제 API는 호출하지 않는다.

#### 닫기·방 지나치기 책임

X·Escape·일반 닫기와 탐색의 돌아가기는 호스트의 navigate(null) 한 경로로 처리한다. 오버레이 배경 클릭만으로는 닫지 않는다. A가 공통 헤더·X와 전체 탐색의 돌아가기·포커스 관리를 제공하며 B는 별도 X를 중복 생성하지 않는다. 탐색 페이지에는 dialog/aria-modal이나 Tab 포커스 trap을 적용하지 않는다. 탐색 보상과 비밀의 방도 같은 전체 화면을 사용하며, 안전지대와 최종 결과는 기존 모달 규칙을 사용한다.

- 전체 탐색 화면: X 대신 돌아가기. navigate(null)로 숲 메인에 복귀하며 시간·체력·서버 데이터 변경 없음. 요청 중에도 복귀 가능하며 이미 보낸 탐색은 취소하지 않음.
- 일반 창/결과 창: 즉시 로컬 닫기. 서버 변경 없음.
- 비밀의 방 창의 방이 미개방이고 immediateAvailable=true이며 RUNNING, 그리고 idle·pending없음이면: 위치ID를 캡처→창은 즉시 닫기→DEFER_ROOM을 **호스트에서1회** 실행. View.destroy에서 defer하지 않는다. 다시 방문 가능 상태는 다음 snapshot으로 갱신한다.
- 이미 개방/즉시 기회 종료/게임 종료 상태면 단순 닫기. remembered/revisit 상세 닫기에 불필요한 defer를 만들지 않는다.
- busy 또는 hasPending 중 X는 언제나 로컬 닫기만 한다. 진행 중 요청을 abort하거나 defer를 병렬/예약 전송하지 않는다. 닫아도 결과 확인은 전역 재시도/메시지에서 계속한다. 성공 응답이 와도 닫은 일반 창을 멋대로 다시 열지 않는다(새 terminal 결과 자동 표시만 예외).
- 방의 ‘나중에 들어간다’는 DEFER_ROOM을 한 번 보낸 후 메인으로 이동한다. 들어간다와 나중에 들어간다는 열쇠가 있어야 활성화되지만 나중 선택은 열쇠·시간·체력을 소비하지 않는다. 별도 헤더 ‘메인으로 돌아가기’는 열쇠가 없어도 사용할 수 있다.
- 탐색 보상 화면에는 X/돌아가기 대신 확인 버튼을 둔다. Escape도 ACK와 같은 확인이다. 이미 처리된 EXPLORE를 재전송하지 않는다. 보상 화면은 호스트만 생성하고 navigate로 직접 열지 않는다.
- requestAnimationFrame 이외 실제 게임시간 감소용 setInterval 금지. 만료는 행동 후 snapshot만 반영.

### C6. DOM·CSS·이미지 공통 규격

A가 아래 host 구조를 제공한다. B는 #se-panel-content 아래만 변경한다. 탐색 선택·탐색 보상·비밀의 방은 #se-exploration-page 전체 화면이며, 안전지대·최종 결과는 기존 오버레이를 사용한다. main/HUD는 두 표면 밖에 남는다.

```html
<div id="se-app">
  <main id="se-main"></main>
  <section id="se-exploration-page" class="se-exploration-page" aria-labelledby="se-exploration-title" hidden>
    <header class="se-exploration-page__header">
      <button id="se-exploration-back" type="button">← 돌아가기</button>
      <div><p>오염 도시 입구</p><h2 id="se-exploration-title" tabindex="-1">어느 길로 갈까?</h2></div>
    </header>
    <div id="se-exploration-content"></div>
  </section>
  <section id="se-messages" aria-live="polite"></section>
  <button id="se-retry" type="button" hidden>다시 시도</button>
  <div id="se-overlay" hidden>
    <section class="se-dialog" role="dialog" aria-modal="true" aria-labelledby="se-panel-title">
      <header class="se-dialog__header">
        <h2 id="se-panel-title"></h2>
        <button id="se-panel-close" type="button" aria-label="닫기">×</button>
      </header>
      <div id="se-panel-content"></div>
    </section>
  </div>
</div>
```

A의 common.css에서만 html/body/전역box-sizing/공통 토큰을 정의한다. B는 `body`, `button`, `input`, `*`, `:root`, `.se-dialog` 같은 전역 선택자를 추가하지 않는다. B CSS는 `.se-safe …`, `.se-result …`, A 기능 CSS는 `.se-main …`, `.se-explore …`, `.se-room …`로 범위를 제한한다. 역할별 SVG symbol/DOM id도 se-safe-/se-result-/se-main-/se-explore-/se-room- 접두사. 재료 DOM key는 batchId다.

| common.css 토큰 | 기본값 |
|---|---|
| --se-color-bg | #101714 |
| --se-color-panel | #1b2520 |
| --se-color-text | #eef3eb |
| --se-color-muted | #b7c4b9 |
| --se-color-accent | #b5d783 |
| --se-color-warning | #f0b35a |
| --se-color-danger | #f08080 |
| --se-color-border | #46544b |
| --se-space-1 /2 /3 /4 /6 | 4px /8px /12px /16px /24px |
| --se-radius | 12px |
| --se-hud-top / --se-hud-bottom | 132px /208px |

폰트는 `system-ui, -apple-system, "Apple SD Gothic Neo", "Malgun Gothic", sans-serif`. 버튼 높이 최소40px, X는44×44px. 색상만으로 상태를 표시하지 않고 텍스트/기호 병행. B는 각 var에 같은 기본값 fallback을 두어 독립 preview에서도 표시되게 한다. 공통 토큰 이름을 임의로 추가·변경하지 않는다.

메인 배경은 화면 전체지만 HUD는 좌상단 시간, 좌하단 상태, 우하단 휴대품. 중앙 큰 이동 지점2개: 숲/추락 비행기=안전지대, 폐허 도시=탐색. 안전지대·결과 등 모달은 위132px·아래208px HUD 영역을 침범하지 않는 중앙, 좌우24px 이상 여백·최대너비1200px. 높이는 남는 공간에서 스크롤하며 min-height 때문에 HUD 위로 넘치지 않게 한다. 낮은 해상도에서는 내부 콘텐츠를 스크롤하고 실행·X를 가리지 않는다. 전역 메시지/재시도는 HUD 사이 하단 중앙에 두고 독립적인 스크롤 표면을 침범하지 않는다.

전체 탐색은 도시 입구/갈림길 배경으로 전환한다. 공통 비용·확률은 상단에 작게 모으고, 지역별 이미지·이름·설명·후보·특수 보상은 3×2 카드로 표시한다.1280×720/1440×900/1920×1080에서 스크롤 없이 여섯 카드를 표시한다. 탐색 장면의 하단HUD는 동일 정보를 더 작게 배치하며, 돌아가면 기존 크기로 복원한다. 전체 탐색의 표면(root)은 #se-exploration-content이고 B의 모달 root는 바뀌지 않는다.

오버레이 표시 중 하위 월드 이동 버튼만 비활성화/포커스 제외, HUD 텍스트는 계속 보인다. dialog 내부 포커스 trap·열 때 제목/첫 컨트롤로 포커스·닫을 때 진입 버튼으로 복귀는 A의 호스트 책임. X는 busy여도 활성. 결과 경고/오류는 보이는 텍스트와 aria-live로 알린다.

이미지는 로컬 소유 폴더에 둔다. 원격 URL/절대 개인PC 경로 금지. 파일명은 영문소문자·하이픈. 이미지 없으면 같은 종횡비의 CSS/로컬SVG 대체 화면으로 기능 개발을 진행한다. A의 장소 이미지는 location-1~6 매핑으로 관리하고, B는 A 파일을 수정하거나 외부에서 hotlink하지 않는다. 외부 이미지를 넣으면 사용 가능한 출처/권리를 담당자별로 남긴다. 특정 상용게임 UI를 그대로 복제하지 않는다.

### C7. 실행·검증·Git 공통 절차

먼저 A가 기준 커밋과 이 문서가 원격에서 접근 가능한지 확인한다. **원격 업로드가 확인되지 않으면 팀원에게 클론 가능하다고 안내하지 않는다.** 사용자 개인 MySQL 비밀번호·경로를 공유하지 않는다.

```bash
git clone https://github.com/CallSuu/ScrapEscape.git
cd ScrapEscape
git fetch origin
git cat-file -e 'e339210^{commit}'
```

마지막 명령 실패면 다른 기반으로 진행하지 말고 A에게 기준 브랜치/커밋 업로드를 요청한다. 성공 후 각 담당자는 자기 작업 브랜치를 생성한다(담당 절에 명시). 이미 저장소가 있으면 clone하지 말고 현재 작업을 보존한 뒤 fetch한다. checkout/reset/force로 다른 작업을 버리지 않는다.

MySQL 접속 후 빈 전용 DB를 만든다(이미 있으면 그대로 사용).

```sql
CREATE DATABASE IF NOT EXISTS scrap_escape CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

저장소 루트에 Git 제외 `application-local.properties`를 만들고 각자 접속 계정·비밀번호만 입력한다.

```properties
spring.datasource.username=본인MySQL계정
spring.datasource.password=본인MySQL비밀번호
```

Java17 SDK·Gradle JVM, IntelliJ 작업 디렉터리=저장소 루트. `./gradlew bootRun` 또는 ScrapEscapeApplication 실행. 기본 http://localhost:8080 . MySQL은 로컬에서 별도 실행. 비밀번호/로컬설정/DB덤프/work·build/.idea를 커밋하지 않는다.

A는 `/`, B는 `/dev/safe-zone.html`에서 확인한다. 해당 페이지는 각 담당자가 구현해야 한다. HTML을 file://로 열거나 Live Server 별도 포트에서 API를 호출하지 않는다. 외부 배포/CORS 추가는 이번 범위 밖.

검증 명령:

```bash
./gradlew test bootJar
node --test src/test/javascript/*.test.mjs
# 작성한 각 .mjs 파일별 문법 확인 예:
node --check src/main/resources/static/js/safe-zone.mjs
```

MySQL 통합 테스트는 별도 scrap_escape_test DB/계정 권한 준비 후 아래처럼 실행. 일반 test만으로는 환경 변수 없는 DB 테스트가 skip되므로115개 통과라고 보고하지 않는다. Node는 테스트 도구로만 필요하다.

```bash
MYSQL_TEST_URL='jdbc:mysql://127.0.0.1:3306/scrap_escape_test?serverTimezone=Asia/Seoul' ./gradlew test --rerun-tasks
```

전체 오류/콘솔 예외/정적파일404/모듈export 오류가 없어야 한다. JAR 빌드만 성공해도 JavaScript import가 맞다는 뜻은 아니다. 실제 브라우저에서 화면/버튼/네트워크까지 확인한다. 테스트DB 생성·트리거·임시 migration DB 권한은 README 참고.

완료 보고: 수정 파일 목록, 실행 명령과 실제 결과(미실행·skip 포함), 각 해상도 캡처, API 실패/재시도 확인, 알려진 제한. 의미 있는 새 상태 분기에는 적절한 회귀 검증을 추가하되 구현을 그대로 복사한 테스트로 수량만 늘리지 않는다.

Git 공통:

- A/B 모두 기준e339210에서 자기 브랜치. A는 `frontend/main-exploration`, B는 `frontend/safe-zone`.
- 커밋은 자기 소유 파일과 필요한 담당 기록만 선택적으로 stage. `git add .`로 다른 담당 파일/로컬 설정을 섞지 않는다.
- 공통 파일 변경/백엔드 오류 발견은 먼저 공유하고 소유자가 수정한다. API가 부족하다는 이유로 가짜 서버/임의 응답 필드를 만들지 않는다.
- 최종 통합 소유자=A. B는 자기 브랜치를 푸시하고 커밋ID/파일 목록을 전달한다. A가 frontend/main-exploration에 **merge**한다. 같은 변경을 cherry-pick한 뒤 다시 merge하지 않는다. 충돌 시 소유자 기준으로 검토하며 전체 theirs/ours 강제 적용 금지.
- 두 브랜치를 따로 main에 합치기보다, 먼저 A 브랜치에서 두 화면을 통합 검증하고 그 후 main 반영을 결정한다. main 병합/원격 푸시는 사람이 승인한 범위에서만 수행한다.
- 이 문서 요청은 명세서 작성이며 자동 브랜치 생성·프론트 구현·커밋·푸시를 요구한 것은 아니다.

<!-- COMMON-END -->

## B1. 팀원 담당 범위와 파일

작업 브랜치는 `frontend/safe-zone`이다. 생성 명령은 `git switch -c frontend/safe-zone e339210`. 이미 같은 브랜치가 있으면 새로 만들지 말고 상태부터 확인한다.

팀원 B는 안전지대와 결과 화면의 제품 모듈을 구현한다. 생성할 파일은 다음과 같다.

- `src/main/resources/static/js/safe-zone.mjs`: `createSafeZoneView(root, context)`를 named export.
- `src/main/resources/static/js/result.mjs`: `createResultView(root, context)`를 named export.
- `src/main/resources/static/css/safe-zone.css`, `result.css`: 각각 `.se-safe`, `.se-result` 안에만 적용.
- 필요한 이미지: `static/assets/safe-zone/**`, `static/assets/result/**`.
- 독립 확인 페이지: `static/dev/safe-zone.html`, `static/dev/safe-zone-preview.mjs`.
- 필요한 회귀 검증: `src/test/javascript/safe-zone.test.mjs`, `result.test.mjs`.

`index.html`, `app.mjs`, `common.css`, A의 화면 파일, 기존 `game-api.mjs`는 수정하지 않는다. 미리보기를 위해 별도 운영 index를 만들면 병합 충돌이 생기므로 개발용 페이지를 사용한다.

진행 순서는 공통 인터페이스로 두 View 생성 → 독립 개발 페이지 연결 → 재료 관리 → 휴식·치료 → 수리·탈출 → 결과 통계 → 상태별 검증 → 브랜치 인계다.

## B2. A 코드 없이 실행하는 개발용 페이지

주소는 `http://localhost:8080/dev/safe-zone.html`이다. 이 페이지와 host 코드는 B가 만든다. 페이지 진입 시 게임을 자동 생성하지 말고 ‘테스트 판 시작’ 버튼을 제공한다.

`dev/safe-zone-preview.mjs`는 다음 모듈을 가져온다.

```javascript
import { createGameClient } from '../js/game-api.mjs';
import { createSafeZoneView } from '../js/safe-zone.mjs';
import { createResultView } from '../js/result.mjs';
```

CSS는 `/css/safe-zone.css`, `/css/result.css`를 가져온다. 개발 페이지의 기본 글꼴·배경은 개발용 HTML 내부에 한정하고, 제품 CSS는 공통 토큰에 같은 기본값 fallback을 둔다. A의 common.css가 없어도 사용할 수 있어야 한다.

개발용 host도 C5의 Context 계약을 그대로 구현한다.

- 실제 `createGameClient`를 하나만 생성하고 `latestScreen`, `flags`, 현재 panel을 보관한다.
- 같은 Command→API 매핑과 DispatchResult 형식을 사용한다. 필요한 오류는 catch해 전역 메시지로 표시한다.
- ‘안전지대’, ‘결과 보기’, X, 전역 ‘다시 시도’를 연결한다. 결과 보기는 종료 판에만 허용한다.
- 테스트 재료가 필요하면 개발 페이지에만 ‘병원 탐색’(EXPLORE, locationId=6), ‘6시간 휴식’을 추가해 실제 API로 얻는다. 원하는 재료가 안 나온다고 프론트에서 보상을 생성하지 않는다.
- 빠른 경계 상태 확인은 View 테스트에서 저장소의 실제 응답 JSON을 읽어 주입해도 된다. 이 경우 ‘읽기 전용 예제’임을 명시하고 실제 변경 API를 호출하지 않는다. 운영 View에는 예제 분기나 가짜 보상을 넣지 않는다.

제품 View는 자신이 개발 host에서 실행되는지 운영 host에서 실행되는지 알 필요가 없어야 한다. A에게 인계할 것은 두 factory와 CSS다. 개발 페이지가 동작하는 것과 두 담당자의 화면 통합 완료는 별개로 보고한다.

## B3. 안전지대 배치와 로컬 상태

전달받은 root 아래에 `.se-safe` 컨테이너를 만든다. 공통 창 제목·X·포커스 관리는 host가 제공하므로 중복 생성하지 않는다. 개발용 host도 같은 껍데기를 제공한다.

기본 배치는 3열이다. 좌측 약230px에는 상태·휴식·치료, 중앙 남은 공간에는 휴대품·상자, 우측 약300px에는 수리·탈출을 배치한다. 간격은16px을 기준으로 한다. 좁거나 낮은 화면에서는 내부 스크롤을 사용하고 HUD 영역을 덮지 않는다.

| 영역 | 내용 | 데이터 |
|---|---|---|
| 좌측 상태 | 체력·오염·질병·치료 기한·연속 탐색·보급품 | `screen.state` |
| 좌측 휴식 | 1~8h 선택, 예상 완료 시각·회복량·오염 변화·기한 경고 | `actions.rests`를 hours로 조회 |
| 좌측 치료 | 키트 수, 가능 여부·이유, 예상 오염·질병·실패 | `actions.treatment` |
| 중앙 휴대품 | 재료와 빈칸, 묶음 선택 | `state.inventory/inventoryCapacity` |
| 중앙 상자 | 재료와 빈칸, 묶음 선택 | `state.chest/chestCapacity` |
| 중앙 선택 행동 | 위치·수량·상태·잔여 시간, 이동·세척·폐기 | `actions.items`를 batchId로 조회 |
| 우측 수리 | 선택된 부품, 재료 보유/필요량, 완료 상태 | `state.repairs`와 `actions.repairs` |
| 우측 하단 | 완료 n/4, 탈출 버튼·제한 이유 | `actions.escape` |

로컬 상태는 선택 휴식 시간(초기6h), 선택 batchId, 폐기 확인 정도만 둔다. 새 screen에서 선택한 batchId가 사라지면 선택과 폐기 확인을 해제한다. 같은 이름의 다른 묶음으로 자동 대체하지 않는다. gameId가 바뀌면 모든 선택을 초기화한다. render가 호출될 때마다 휴식 시간을 초기값으로 되돌리지 않는다.

선택 재료가 없으면 ‘재료를 선택하세요’를 표시한다. 시간 선택이나 재료 선택만으로 서버 변경 요청을 보내지 않는다.

## B4. 재료 관리

### 묶음과 슬롯

- 각 타일에는 이름·수량·상태·잔여 시간을 표시한다. materialId가 같아도 batchId가 다르면 별도 타일이다.
- `DIRTY`는 ‘미세척’과 남은 n시간, `CLEAN`은 ‘세척 완료’, `UNUSABLE`은 ‘사용 불가’로 표시한다.
- DIRTY는12h 이하 주황·6h 이하 빨강을 쓰고 텍스트도 함께 제공한다. CLEAN/UNUSABLE의 null 잔여 시간을0시간으로 표시하지 않는다.
- 점유 칸은 배열 길이, 빈칸은 정원−배열 길이다. 세척액·키트·열쇠를 재료 타일로 만들지 않는다.
- 타일과 행동은 batchId로 연결한다. 배열의 순번을 식별자로 사용하지 않는다.

### 이동

선택 묶음의 `ItemActionPreview.moveTo`를 그대로 `MOVE_ITEM` 명령에 전달한다. 목적지가 CHEST면 ‘상자에 넣기’, INVENTORY면 ‘꺼내기’다. 전량 이동만 지원하며 수량 분할이나 드래그 삭제를 새로 만들지 않는다.

실행 가능 여부는 `move.allowed`와 Flags를 함께 사용한다. 상자가 가득 차도 기존 CLEAN 묶음에 합칠 수 있으면 서버가 허용할 수 있으므로 배열 길이만으로 차단하지 않는다. 성공 후 원래 batchId가 사라져도 정상이다. 최신 screen으로 타일을 다시 그린다.

### 세척

`clean.allowed`, 이유, 시간·체력·세척액 비용, projected를 표시한다. 액1회분으로 선택 묶음 전체를 세척한다. 철4개 묶음에 액4회분이 필요한 것은 아니다. 명령은 `CLEAN_ITEM{batchId}`다.

`clean.projected.expiringBatchIds`에 있는 다른 묶음은 이 행동 중 만료될 수 있음을 알린다. projected가 null이면 이 목록을 읽지 않는다. 선택 세척 재료의 보호 여부도 서버 미리보기를 따른다. 시간 감소·만료·재료 병합을 프론트에서 직접 수행하지 않는다.

### 폐기

`discard.allowed`와 Flags를 사용한다. 버튼을 누르면 같은 root 내부에 ‘[이름] [수량]개 묶음 전체 폐기’ 확인 영역과 확인·취소 버튼을 연다. 확인 영역의 취소/×는 로컬 확인만 닫으며 전체 안전지대 X와 구분한다.

확인 전에는 DELETE를 보내지 않는다. 확인 후 `DISCARD_ITEM{batchId}`를 한 번 전송한다. 성공 후 타일·통계를 갱신하고, 오류면 재료를 지운 것처럼 표시하지 않는다. UNUSABLE도 직접 폐기해야 하므로 숨기거나 자동 삭제하지 않는다.

새 snapshot이나 flags가 오면 확인 버튼도 다시 평가한다. pending 중에는 폐기 확인 실행을 막되 로컬 취소는 가능하다.

## B5. 휴식·치료·수리·탈출

### 휴식

1~8h만 선택한다. `actions.rests.find(r => r.hours === selectedHours)`로 미리보기를 찾는다. 시간 선택 자체로 게임 시간은 흐르지 않는다.

실제 회복량은 `projected.stamina - state.stamina`, 오염 변화는 `projected.contamination - state.contamination`이다. 체력3에서6h 휴식이면10까지+7이고, 이미10이어도 휴식 가능하다. projected.status가 FAILED면 경고하되 allowed=true인 행동은 허용한다. 실행은 `REST{hours}`다.

### 치료

질병이 없어도 서버가 허용하면 예방 사용이 가능하다. 키트가 없으면 제한 이유를 표시한다. 정상 치료는1h·키트1·오염−60(최소0)·질병 제거다.

완료 시 기한에 도달하는 경우는 projected의 FAILED·키트 보존·질병 잔존을 그대로 보여준다. ‘치료하면 무조건 생존’이라고 안내하지 않는다. `TREAT`에 임의의 회복량·오염값을 추가하지 않는다.

### 수리

`state.repairs`와 `actions.repairs`를 partId로 연결한다. ingredients.availableQuantity는 이미 양쪽 저장소의 CLEAN을 합산한 값이다. DIRTY/UNUSABLE을 더하거나 한 저장소의 재료만 계산하지 않는다.

부품 카드에는 이름·재료별 보유/필요량·완료 상태를 표시한다. 실행은 actions.repairs의 preview.allowed를 사용하고3h·체력0·기한 위험을 표시한다. 명령은 `REPAIR_PART{partId}`다. 제작과 장착은 한 행동이며 완성품을 인벤토리에 추가하지 않는다.

4개 수리 완료만으로 결과 창을 열지 않는다.

### 탈출

`actions.escape.allowed`로 버튼을 설정한다. 미완성이면 이유와 현재 완료 개수를 보여준다. 모두 수리한 후 사용자가 `ESCAPE`를 선택한다.

새 screen.status가 SUCCESS/FAILED가 되면 host가 결과 View를 연다. SafeZoneView.render 안에서 navigate나 dispatch를 호출하지 않는다. 수리·휴식·치료 중 FAILED가 돼도 같은 원칙을 적용한다.

## B6. 결과 화면

`createResultView`는 screen.state.status가 SUCCESS/FAILED일 때만 결과를 표시한다. RUNNING/null이면 ‘아직 종료되지 않았습니다’를 표시한다. X와 결과 재열기는 host 책임이다.

- SUCCESS 제목은 ‘탈출 성공’, FAILED는 ‘생존 실패’.
- TIME_LIMIT은 ‘생존 제한 시간 초과’, UNTREATED_DISEASE는 ‘질병 치료 기한 초과’. 그 외/null이면 일반 실패 안내를 쓰고 원인을 추측하지 않는다.
- 소요 시간은 elapsedHours, 게임 날짜·시각은 day/hour로 구분한다. 날짜를 다시 계산해 소요 일수를 임의로 고치지 않는다.
- state.repairs에서 완료된 부품 이름과 완료 n/4를 표시한다. 수리 소비량을 별도 추정하지 않는다.

| 통계 | 표시할 필드 |
|---|---|
| 탐색 | `statistics.explorations/dayExplorations/nightExplorations/emptyExplorations` |
| 재료 | `statistics.collected/cleaned/discarded` |
| 생존 | `statistics.diseaseCount/treatmentCount/medicalKitsUsed/maxContamination/stormExposureHours` |
| 방·열쇠 | `secrets.statistics.discoveredRooms/openedRooms/unopenedRooms/keysAcquired/keysUsed` |
| 방 결과 | `secrets.statistics.goodOutcomes/badOutcomes/materialsCollected/materialsLost` |

재료 획득 통계는 실제로 담은 수량이다. discarded는 직접 폐기한 개별 수량이며 수리 소비·방 손실이 아니다. explorations는 일반 탐색이며 방 재방문을 합산하지 않는다. 값0도 누락하지 않는다.

‘새 게임’은 `context.dispatch({type:"START_GAME"})`로 시작 화면에 복귀한다(screen/result=null). 시작 화면에서 다시 시작해야 새 판이 생성된다. busy/pending이면 복귀도 차단한다. 결과 표시만으로 DELETE·DB 정리·순위 등록을 호출하지 않는다. 현재 종료 판이 DB에 남는 것은 정상적인 보류 정책이다.

## B7. 완료·인계 기준

- [ ] 소유 범위 밖 코드·API·common.css·index.html·app.mjs를 수정하지 않았다.
- [ ] 두 named factory가 정확히 export되고 render/destroy와 공통 Context만 사용한다.
- [ ] 개발 페이지가 A 모듈 없이 실행된다. 자동POST·제품 fixture·View별 클라이언트가 없다.
- [ ] 휴대4칸·상자16칸·묶음6개·획득 시각별 DIRTY 타일·만료 표시가 맞다.
- [ ] 가득 찬 목적지의 CLEAN 병합 가능/미세척 이동 불가·이유·선택 ID 갱신을 확인했다.
- [ ] 체력·액 부족·사용 불가 재료의 세척 차단, 묶음 전체 세척과 타 재료 만료 경고를 확인했다.
- [ ] 폐기 확인·취소가 있고 확인 전 요청이 없다. 사용 불가 재료가 자동 삭제되지 않는다.
- [ ] 휴식1~8h·회복 상한·오염 감소·질병 기한 경고·최대 체력 휴식이 맞다.
- [ ] 키트 없음·예방 사용·정상 치료·기한 도달 치료 실패를 서버 미리보기대로 표시한다.
- [ ] 선택4개 부품·CLEAN 보유량·수리 완료·별도 탈출을 확인했다.
- [ ] 성공/두 실패 이유·전체 통계·0값·방/열쇠 통계·결과 후 새 게임을 확인했다.
- [ ] 반복 render·재열기 후 한 클릭당 요청1개이며, 폐기·병합 후 오래된 선택이 남지 않는다.
- [ ] 3개 해상도·스크롤·비활성 이유·X 영역·콘솔/정적파일·문법/테스트 결과를 확인했다.

인계 메모에는 브랜치/커밋ID, 수정 경로, 두 factory export, 사용 Command, 로컬 검증 URL, 검증·미검증 항목, 임시 이미지·알려진 제한을 기록한다. A에게 파일을 따로 복사시키지 말고 브랜치를 merge하도록 전달한다.

## B8. AI에게 그대로 전달하는 작업 프롬프트

```text
너는 Scrap Escape 프론트엔드 담당 B(안전지대/결과)다. 첨부 기획안과 이 개발 명세서 전체를 읽고 작업하라.
저장소: https://github.com/CallSuu/ScrapEscape. 백엔드 기준: e339210. 작업 브랜치: frontend/safe-zone.
먼저 경로·AGENTS.md·Git 상태·기준 커밋을 확인하고 기존 변경을 보존하라. 기준 커밋을 fetch할 수 없으면 다른 API로 추측하지 말고 A에게 알리라.
제품 파일은 safe-zone.mjs/result.mjs/safe-zone.css/result.css와 소유 assets다. 독립 실행은 /dev/safe-zone.html과 dev/safe-zone-preview.mjs에서 준비하라.
index.html/app.mjs/common.css/A 화면/백엔드/DB/기존 game-api.mjs를 수정하지 마라. 새 프레임워크·의존성·빌드 방식도 추가하지 마라.
공통 계약v1.3의 createSafeZoneView(root, context), createResultView(root, context)를 named export하고 render(screen, flags)/destroy()를 반환하라.
각 View는 자기 root만 렌더링하고 통신은 context.dispatch로만 처리하라. 제품 View에 fetch/client/UUID/자동 재시도/다른 View import를 넣지 마라.
안전지대는 상태·휴식·치료, 양쪽 인벤토리·이동·세척·폐기,4개 수리·탈출을 구현하라. 결과는 서버 성공/실패와 전체 통계·방/열쇠 통계를 표시하라.
현재 백엔드16개API·미리보기·불가 이유·만료·병합·치료 기한·중복 방지는 이미 있다. 프론트에서 시간·오염·재료·성공을 직접 변경하지 마라.
allowed=false는 비활성, projected.status=FAILED는 경고하되 allowed=true면 실행 가능이다.4개 수리 후 별도 탈출POST가 필요하다.
선택은 batchId/partId/hours로 연결하고 이벤트를 중복 등록하지 마라. HUD·공통X·재시도·종료 전환은 host 책임이다.
결과 저장·삭제·랭킹·로그인·이어하기는 범위 밖이다. 기획과 API가 다르면 독자적으로 보정하지 말고 차이를 보고하라.
실제 서버/개발 페이지·브라우저·완료 기준·빌드/테스트를 확인하고 변경 파일·검증·미검증·인계 메모를 보고하라.
커밋·푸시·main 병합은 별도 요청 전 수행하지 마라.
```

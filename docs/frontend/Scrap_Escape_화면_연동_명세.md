# Scrap Escape 화면 연동 명세

기준일2026-10-27. 승인한 세 가지(표시/제한 매핑, 서버 미리보기, 통신 공통 처리)의 구현 계약. 실제 배경·HUD·모달 디자인/배치는 다음 작업이다.

분업 구현은 다음 두 명세서의 공통 계약v1.2을 사용한다. Context·모듈 factory는 앞으로 구현할 계약이며, 기존 통신 모듈과 구분한다.

- [본인 A 개발 명세서](Scrap_Escape_본인담당_개발명세서.md)
- [팀원 B 개발 명세서](Scrap_Escape_팀원담당_개발명세서.md)

## 1. 연결 방식

Spring Boot가 정적 HTML/CSS/JavaScript와 API를 같은 localhost 주소에서 제공하는 방식을 기본으로 한다. JavaScript ES module `src/main/resources/static/js/game-api.mjs`를 `/js/game-api.mjs`에서 가져온다. 프레임워크·프론트 빌드 도구·새 라이브러리 설치가 필요 없다. Node는 JavaScript 테스트 실행 도구로만 사용하며 백엔드 실행은 Java/Spring Boot다.

새 게임 POST 이후와 매 행동 완료 후 SE-16 `GET /api/games/{id}/screen`을 조회한다. State·탐색 카드·안전지대 미리보기를 한 DB 읽기 트랜잭션으로 가져온다. 데이터 조회는 시간을 진행하지 않는다. 실제 시계 타이머·주기 조회가 필요 없다.

## 2. 화면 영역과 응답 연결

| 화면/위치 | 표시 정보 | 응답 경로 | 실행·제한 |
|---|---|---|---|
| 항상·왼쪽 위 | 날짜/시각, 낮·밤, 탈출 제한까지 시간 | state.day/hour/night/remainingHours | 고정 HUD. remainingHours는 생존 제한이며 즉시 탈출 가능 시간과 구분 |
| 항상·왼쪽 위 경고 | 현재/다음 폭풍, 질병 치료 기한 | state.currentStorm/nextStorm/treatmentRemainingHours | null이면 해당 표시 숨김. 시간은 경과 기준을 날짜/시각으로 변환 |
| 항상·왼쪽 아래 | 체력/최대체력, 오염도, 질병, 연속 탐색 | state.stamina/maxStamina/contamination/diseased/consecutiveExplorations | 안전지대·탐색 선택창에도 같은 상태 유지 |
| 항상·오른쪽 아래 | 휴대 재료 슬롯, 세척액·의료 키트·열쇠 게이지 | state.inventory/inventoryCapacity, cleanerAmount/Capacity, medicalKitAmount/Capacity, secrets.keyAmount/keyCapacity | 재료만 슬롯 사용. 보급품은 별도 수치 |
| 재료 아이콘 | 이름·수량·세척 상태·만료까지 시간 | Item.condition/quantity/expiresInHours | DIRTY만 잔여시간 표시. 12h 이하 주황·6h 이하 빨강, UNUSABLE 사용 불가 표시. 표시로 DB 변경하지 않음 |
| 안전지대·상세 | 상세 상태, 양쪽 재료, 보관함 슬롯 | state, state.chest/chestCapacity | 빈칸은 묶음 행 수 기준. 같은 종류 DIRTY도 획득시각별 별도 칸 |
| 안전지대·재료 행동 | 이동·세척·폐기 버튼, 비용/예상값/제한 이유 | actions.items[batchId].move/clean/discard | moveTo가 목적지. 각 SafeActionPreview.allowed 사용 |
| 안전지대·휴식 | 1~8h 선택, 회복/오염 감소·완료시각 | actions.rests[hours].preview | 체력 최대도 휴식 가능. 기한 위험은 경고하고 선택 허용 |
| 안전지대·수리 | 4개 부품, 재료 보유량/필요량, 완료 표시 | state.repairs, actions.repairs[partId].preview | 미수리·CLEAN 재료 충족 시 가능. 3h 제작/장착 한 행동 |
| 안전지대·치료 | 치료 비용·오염/질병 예상 | actions.treatment | 키트 없으면 비활성. 실패 기한에 도달하는 치료는 실패 예상 표시 |
| 안전지대·탈출 | 탈출 버튼과 미완성 이유 | actions.escape | 수리4개 완료 후 가능. 결과 예상 SUCCESS/FAILED 구분 |
| 탐색 선택창 | 장소 이미지·설명·예상 보상·시간·체력·오염/질병 확률 | locations[] | canExplore를 allowed로 연결. unavailableReason 표시. 무작위 재료는 예상 후보/확률로 표시 |
| 비밀의 방 | 기억한 장소에 ? 버튼, 입장 방식별 비용/경고 | locations[].secretRoom 또는 state.secrets.rooms | 발견 전null·버튼 숨김. 표시문구는 ?만. immediate/revisit.allowed와 이유 사용 |
| 방 입장 선택 | 즉시 입장/재방문/지나치기 | SecretRoomCard.immediate/revisit/immediateAvailable/opened | 열쇠가 있어도 자동 입장 없음. opened면 재입장 금지. 읽기 자체는 즉시 기회를 소모하지 않음 |
| 모든 선택창 | X 닫기 | 화면 로컬 상태 | X는 자원 부족·통신 처리 중에도 닫을 수 있음. 처리 중 서버 행동을 취소하거나 다시 실행하지 않음 |
| 종료 결과 | 성공/실패 사유·시간·수리·탐색·세척·질병·방·열쇠 통계 | state.status/failureReason/statistics/secrets.statistics/repairs | 종료 판 조회 가능. 최종 요약 저장·삭제·순위표는 별도 정책 미정 |

방에서 X/지나치기는 미개방·즉시 입장 가능·진행 중인 판이며 통신/pending이 없을 때 호스트가 SE-14 defer를1회 전송해 즉시 입장 기회를 종료한다. 즉시 기회가 이미 끝난 기억한 방 상세나 개방 이력은 로컬 닫기만 한다. **다른 행동이 진행 중일 때 X를 누르면 화면만 닫고 defer를 끼워 보내지 않는다.** 방 데이터가 서버에 남더라도 다시 열 때 최신 screen을 기준으로 표시한다. 일반 선택창 X는 API 없이 닫는다.

## 3. 미리보기·비활성화 규칙

- allowed=false인 실행 버튼만 회색·disabled로 만든다. 카드·비용·재료·제한 이유는 계속 보인다. 색만으로 구분하지 않고 버튼 가까이에 이유 텍스트를 표시한다.
- 서버 제공 projected 값으로 예상 변화량(예상−현재)을 계산해 표시한다. 예: 체력3에서6h휴식은 예상10이므로 실제 회복+7. 무조건+8이라고 표시하지 않는다.
- projected=null은 실행 불가다. projected.status=FAILED는 실행 가능하지만 기한 경고가 필요한 경우다. TIME_LIMIT/UNTREATED_DISEASE를 사용자 문구로 표시한다. 기한 때문에 버튼을 임의로 차단하지 않는다.
- 탐색 기한 위험은 durationHours >= state.remainingHours 또는 질병 치료 잔여시간으로 판단한다. 즉시/재방문 방은 기존 deadlineRisk를 사용한다. 확률 보상·발병 결과를 확정처럼 보여주지 않는다.
- 표시 상태는 조회 시점 예상이다. 실제 API의 검증과 오류가 최종 기준이다. 오래 열린 선택창은 진입 시 최신 screen을 조회하며,409 충돌 후에는 다시 조회한 뒤 선택하게 한다.
- CLEAN 병합·전량 소비로 기존 batchId가 사라질 수 있으므로 성공 후 과거 DOM/아이템ID를 유지하지 않고 screen으로 다시 그린다.

## 4. 공통 통신 모듈

`createGameClient({onSnapshot,onBusy,onError})`는 화면 전체에서 하나만 만든다.

| 함수/상태 | 역할 |
|---|---|
| load(gameId) | 전체 screen 조회, onSnapshot 호출 |
| act(method,path,body?) | 새 행동 UUID 생성, 정확한 요청 본문을 고정해 전송 |
| retry() | 응답이 불확실한 행동이면 같은 UUID·본문으로 재전송. 행동 성공이 확인됐고 화면 갱신만 실패했다면 GET만 재시도 |
| refresh() | 현재 게임의 screen 재조회 |
| busy / hasPending | 요청 처리 중 / 결과 확인 또는 화면 갱신이 필요한 요청 존재 |
| onBusy({busy,hasPending}) | 모든 실행 버튼·재시도 버튼의 상태 갱신 |
| onError(error) | code/message/status/retryable. 화면에 message를 textContent로 표시 |
| onSnapshot(screen) | HUD와 열린 창을 같은 최신 데이터로 갱신 |
| applyActionAvailability(button,preview,flags) | disabled·aria-disabled·title 설정, 제한 이유 반환. 반환 텍스트도 화면에 표시 |

예시(화면 DOM을 붙일 때 사용하는 연결 코드):

```javascript
import { createGameClient, applyActionAvailability } from '/js/game-api.mjs';
const client = createGameClient({
  onSnapshot: screen => renderScreen(screen),
  onBusy: flags => renderActionButtons(flags),
  onError: error => { errorText.textContent = error.message; }
});
await client.act('POST', '/api/games');
// 휴식 실행: await client.act('POST', `/api/games/${gameId}/rests`, {hours:6});
// 재시도: await client.retry();
// 이벤트 핸들러에서는 Promise 거부를 catch하여 처리한다(onError에서 문구 표시).
```

renderScreen/renderActionButtons/errorText는 앞으로 작성할 화면의 함수·DOM이며 현재 모듈에 포함된 UI가 아니다. 탐색 버튼 helper 입력은 `{allowed:card.canExplore,unavailableReason:card.unavailableReason}`로 변환한다. 방은 immediate/revisit 객체를 그대로 사용한다.

요청 흐름:

1. 새 행동은 새 UUID. 처리 중 모든 변경 버튼 비활성. 읽기 정보와 X는 유지.
2. 정상 응답은 state.status가 FAILED일 수도 있다. 오류로 취급하지 않고 결과 화면을 그린다.
3. 정상/재사용 응답 모두 screen을 다시 조회한 후 다음 행동을 허용한다. 원래 응답의 과거 State를 화면에 덮어쓰지 않는다.
4. 연결 끊김·15초 타임아웃·잘못된 응답·500이면 수동 ‘다시 시도’를 제공한다. 자동 반복 없음. hasPending 동안 새 행동/새 게임을 막고 같은 요청을 확인한다.
5. 화면 갱신만 실패하면 이미 성공한 행동을 재실행하지 않는다. 다시 시도는 GET만 수행한다.
6.400/404/409 도메인 오류는 실패 내용을 표시하고 요청을 해제한다. 409 후에는 모듈이 최신 screen을 조회해 버튼 상태를 갱신한다. 그 조회도 실패하면 원래409 내용을 유지하며, 다음 창 진입/refresh로 재조회한다. 새로 선택한 행동에는 새 UUID를 쓴다.
7. 재시도 버튼은 hasPending && !busy일 때 활성화한다. 일반 GET 실패는 load/refresh로 재조회한다.

요청 맥락은 페이지 메모리에 보관한다. 페이지 새로고침/탭 종료 후 재시도 번호 복원·이어하기는 구현하지 않는다. 별도 저장 기능을 추가하지 않는 현재 기획 범위다. X는 서버 행동 취소 버튼이 아니다.

## 5. 다음 화면 구현 순서

고정 HUD와 메인 배치 → 안전지대 창(휴식/보관/세척/수리/치료/탈출) → 탐색 카드 → 비밀의 방 선택 → 종료 결과. 배경과 이미지·접근성·해상도별 겹침·실제 브라우저 클릭 검증은 화면 구현에서 확인한다.

## 새 게임 흐름 변경 — 2026-10-27

메인/결과의 새 게임 → 로컬 시작 화면(서버 요청 없음) → 새 게임 시작 클릭 → POST로 새 판 생성 → 메인/HUD. START_GAME 명령은 현재 screen이 있으면 복귀, 없으면 실제 생성이다. 복귀 성공의 screen/result는null. busy/pending 중 복귀는 차단하고 시작 화면에서 REFRESH로 이전 판을 되살리지 않는다.

## 탐색 전체 화면 변경 — 2026-10-27

탐색은 기존 팝업이 아니라 #se-exploration-page의 별도 전체 화면이다. mount는 #se-exploration-content, root Context/명령은 동일하다. 배경을 도시 입구·가까운 건물·갈림길 SVG로 전환하고 X 대신 돌아가기를 사용한다. 공통 정보는 상단, 이미지 중심 지역 정보는3×2 카드. 하단HUD는 같은 값의 압축 배치를 사용한다. 이동/돌아가기 비용 없음. 안전지대·결과 등은 기존 모달을 유지한다. A/B 공통 계약v1.2에 반영했다.

## 탐색 보상·비밀의 방 전체 화면 — 2026-10-27

공통 계약v1.3. EXPLORE/OPEN_ROOM 성공 후 exploration-result(큰 실제 획득 아이콘·수량) → 로컬 ACK → 일반은 메인/새 발견은 방 선택/종료는 B 최종 결과. 비밀의 방도 같은 전체 화면이며 두 선택에 열쇠가 필요하다. 나중 선택은 defer만 하고 자원을 소비하지 않는다. 열쇠 없는 경우에도 헤더 돌아가기로 나갈 수 있다. 배경은 장소별 임시 SVG. [흐름·파일·API·검증](Scrap_Escape_탐색결과_학습가이드.md).

## 안전지대·최종 결과 연결 — 2026-10-27

공통 계약v1.4. B 두 factory와 범위별 CSS가 구현되어 기존 앱의 #se-panel-content에 연결된다. Context/API 변경 없음. 독립 개발 페이지도 같은 createAppController를 재사용한다. [사용법·CRUD·검증](Scrap_Escape_안전지대_학습가이드.md).

## 야영지·정비 별도 화면 — 2026-10-27

공통 계약v1.5. safe-zone은 camp.mjs 전체 화면, camp-action.area는 관리 모달, aircraft-repair는 정비 전체 화면. 하위 X/Back/Escape는 로컬 야영지 복귀, 야영지 Back은 메인이다. 원래 전체 화면 영역과 오버레이를 함께 사용하고 배경은 inert 처리한다. [흐름·코드·검증](Scrap_Escape_야영지_화면_학습가이드.md). API 변경 없음.

## 도시 건물 선택·모달 확장 — 2026-10-27

공통 계약v1.6. exploration-choice는 지역 하나의 정보를 읽고 선택하기로 EXPLORE를 보내는 모달이다. main/page inert, 어두운 overlay, 콘텐츠 높이·넓은 창, 메시지/재시도 footer, 정비 간격16px를 적용했다. [구현/검증](Scrap_Escape_도시선택_창배치_학습가이드.md).


## 2026-10-28 추가 반영: HUD 통일·행동 창 유지·일괄 세척/폐기

모든 화면의 고정 생존 상태와 인벤토리는 안전지대의 크기·글자·패딩을 공유한다. 보관함에서 이동/세척/폐기, 정비에서 수리 완료 뒤에는 현재 창을 유지한다. 휴식/치료는 야영지로 복귀하며 게임 종료는 최종 결과가 우선한다. 오류·응답 미확정은 기존 창과 다시 시도 기능을 유지한다.

선택은 클릭 토글이며 여러 묶음 세척/폐기가 가능하다. 세척은 묶음 수만큼 액과 시간을 사용(묶음당1회분/1h/체력0)한다. 액이 부족하면 버튼 비활성·사유를 표시하고 서버도409 INSUFFICIENT_CLEANER로 전체 취소한다. 양쪽 저장소 혼합 선택 가능. 폐기는 전체 이름·수량 확인 후 한 요청으로 처리하고 취소는 변경 없음. 새 판/선택 변경/사라진 ID는 확인을 초기화한다. 휴식 정보에서 ‘체력 소모 0’만 생략하며 시간·실제 회복량은 유지한다.

추가 명령 CLEAN_ITEMS/DISCARD_ITEMS, 추가 API SE-18/19. docs/api에 최신 명세·OpenAPI·실제 응답 예시를 함께 제공한다.


### 2026-10-28 대화창 크기 통일

메인·야영지·탐색 화면의 하단 대화창은 같은 공통 CSS를 사용한다. 좌측368px/우측448px/하단24px, 최대높이88px, 글자11px/패딩8px 12px. 재시도 버튼도 같은 위치·크기를 사용하며 미확정일 때 대화창을 위로 올린다. 모달 안으로 이동한 대화창은 기존 footer 배치를 유지한다.

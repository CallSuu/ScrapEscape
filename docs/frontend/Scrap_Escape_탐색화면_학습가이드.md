# Scrap Escape 프론트 두 번째 단계 — 탐색 화면

현재 배치 변경: 탐색은 도시 건물→지역 정보 창→선택하기, 모달은 어두운 배경과 넓은 콘텐츠 높이를 사용한다. [최신 안내](Scrap_Escape_도시선택_창배치_학습가이드.md).


기준일:2026-10-27. 담당A의 탐색 카드와 실제 API 연동을 구현했다. 백엔드/API는 변경하지 않았다. 비밀의 방 상세와 팀원B의 안전지대·결과 화면은 후속 단계다.

## 1. 직접 확인

1. IntelliJ에서 서버를 중지 후 다시 실행하고 브라우저의 `http://localhost:8080`을 새로고침한다.
2. 시작 화면에서 ‘새 게임 시작’을 누른다.
3. 메인의 ‘탐색 지역’을 누르면 도시 입구의 전체 화면으로 이동한다.6개 장소 카드를 표시하며 이동 자체는 시간을 소모하지 않는다.
4. 카드의 ‘탐색하기’를 누르면 실제 탐색한다. 완료 후 시간·체력·인벤토리·보급품·열쇠가 서버 값으로 갱신된다.
5. 아래 행동 메시지에서 실제 획득 재료·담지 못한 수량을 확인한다.0개도 정상 결과다.

6개 카드는3열×2행으로 스크롤 없이 표시한다. 공통 비용·확률은 상단, 이름·설명·후보·특수 보상은 이미지 중심 카드에 둔다. HUD는 같은 정보를 더 작게 배치하고 X 대신 돌아가기를 사용한다. 인벤토리나 체력이 부족하면 카드 설명은 계속 읽을 수 있고 실행 버튼만 회색으로 바뀐다. 보관·세척·휴식은 아직 팀원 모듈이 연결되지 않았으므로, 여러 번 탐색 후 진행이 막힐 수 있다. 전체 게임 완성 상태는 아니다.

## 2. 이번 파일의 역할

| 파일 | 역할 |
|---|---|
| [exploration.mjs](../../src/main/resources/static/js/exploration.mjs) | 서버 locations를 카드로 렌더링, 탐색/기억한 방 버튼 처리 |
| [exploration.css](../../src/main/resources/static/css/exploration.css) | 전체 화면3×2·큰 이미지·공통 비용·경고·비활성 스타일 |
| [app.mjs](../../src/main/resources/static/js/app.mjs) | 기존 Context로 탐색 요청, 실제 재료 획득/남김 메시지. 숨겨진 버튼을 키보드 포커스 목록에서 제외 |
| [common.css](../../src/main/resources/static/css/common.css) | 재료별 행동 메시지의 줄바꿈 표시 |
| `static/assets/locations/location-1.svg`~`location-6.svg` | 원본 로컬SVG 임시 장소 그림. 나중에 실제 디자인으로 교체 가능 |

## 3. 읽어볼 핵심 코드

### 서버 목록을 반복해서 카드 만들기

```javascript
for (const location of locations) {
  // locationId에 해당하는 카드 생성/갱신
}
```

`locations`는 서버가 제공한 장소 목록이다. 카드에 적을 장소 이름·재료 후보·비용을 프론트에서 따로 정하지 않는다. Map에 locationId별 DOM을 보관해 요청 상태가 바뀔 때 카드 전체와 키보드 포커스를 매번 없애지 않는다.

### 버튼이 어떤 장소인지 구별하기

```javascript
start.dataset.location = String(locationId);
```

버튼에 장소ID를 붙인다. 클릭 시 현재 screen의 해당 장소를 찾고 다음 명령을 보낸다.

```javascript
context.dispatch({ type: 'EXPLORE', locationId: location.locationId });
```

카드 모듈이 직접 fetch하거나 체력을 빼지 않는다. app.mjs의 공통 처리 → POST explorations → GET screen → render 순서를 사용한다. 서버가 최종 조건을 다시 검사한다.

### 실행 불가와 위험 경고 구분

- `canExplore=false`: 실행 금지. 서버의 unavailableReason을 버튼 옆에 표시한다.
- `busy/hasPending`: 현재 요청을 처리/확인해야 하므로 새 탐색을 막는다.
- 생존/치료 기한 도달 예상: 위험 경고를 표시한다. canExplore가 true이면 실행은 가능하다.
- 질병이 있으면 새 카드에 포함된 추가 시간/체력을 보여준다. 발병 확률을0%라고만 표시해 안전하다고 오해시키지 않는다.

숨겨진 `?` 버튼은 키보드 Tab 목록에서도 제외한다. 정보가 있는 `?`는 열쇠가 없어도 읽을 수 있다. 현재는 다음 단계의 방 상세 모듈을 열 자리만 연결되어 있다.

### 클릭 이벤트는 한 번만 등록

클릭 listener는 factory에서 한 번 등록하고 render에서는 다시 등록하지 않는다. destroy에서 AbortController로 제거한다. 창을 닫았다 다시 열어도 클릭 한 번이 여러 요청으로 불어나지 않게 하기 위해서다.

## 4. 결과 표시 규칙

예상 보상 수량과 실제 담은 수량을 구분한다. 실제 메시지는 다음 형식이다.

```text
재료 1개를 가져왔습니다.
철 조각: 획득 1개
기어: 획득 0개 · 남김 2개
```

자료형은 기존 ActionResult.loot.materials의 quantity/collectedQuantity다. 서로 차이가 있으면 남긴 수량으로 표시한다. 전역 재시도에 성공했을 때도 같은 처리를 사용한다. HUD는 원래 행동 응답의 과거 State가 아니라 재조회한 최신 screen을 따른다.

## 5. 초기 모달형 구현 당시 검증 기록

- JavaScript32개: 기존25개+기한 경고 경계5개+실제 획득/남김·재시도 및 빈손 결과2개 통과.
- MySQL 전체115개: 실패0·오류0·건너뜀0. JAR 빌드 성공.
- 실제 Chrome/전용 scrap_escape_test:6개 카드·이미지 로딩, 창 열기 탐색POST0회, 병원 탐색2회와 키트/체력/시간 갱신, 밤 진입 후7h/체력3 갱신.
- 요청 중 이중 클릭은 탐색1회, X로 닫아도 응답 후 창 자동 재열기 없음, 재열기 후 중복 listener 없음.
- 이번 테스트 판에만 조건을 준비해 인벤토리4칸/체력0 제한·이유·정보 표시, 생존 기한 경고지만 실행 가능, 열쇠0인 기억한 위치 정보 버튼을 확인했다.
- 별도의 브라우저 View 예제에서 악성 형태의 문자열이 HTML로 실행되지 않음, 여러 render 후 클릭1회, busy 중 실행 차단/정보 열람 가능, destroy 후 listener 제거를 확인했다. 예제 dispatch는 실제 API에 연결하지 않았다.
-1280×720·1440×900·1920×1080: 중앙 창과HUD 분리, 가로 넘침 없음, 내부 스크롤. 실제 Tab/Shift+Tab의 포커스 순환·닫기를 확인했다.
- 개인 게임 DB를 변경하지 않았다. 검증용 서버/Chrome은 종료하고 이번 테스트 판만 정리했다.

증거: `work/exploration-java.log`, `work/exploration-final-js.log`, `work/exploration-browser-final.log`, `work/exploration-browser-result.json`, `work/exploration-screenshots/*.png`. work는 개인 검증 산출물로 Git 제외다.

## 6. 다음 단계

비밀의 방 상세창에서 즉시 입장·재방문·열쇠·체력 제한·보상/손실 이력을 연결한다. 그 후 팀원이 구현한 안전지대·결과 모듈과 합친다. 전체 이미지·간격·효과와 플레이 밸런스는 기능 통합 후 다듬는다.

## 7. 현재 전체 화면과 검증

JavaScript34개·MySQL115개 통과와 JAR 빌드 성공. 탐색은 별도 HTML section으로 구현하고 일반 선택창만 모달로 유지한다. app.mjs가 active page를 선택하고 main.mjs/main.css가 도시 배경·압축HUD를 적용한다. explorationSharedInfo는 현재 백엔드의 동일한 비용/확률 값을 상단에 모은다. 장소별 의료 키트만 해당 카드에 남긴다.

실제 Chrome에서1280×720·1440×900·1920×1080의6개 카드/사진 영역/버튼이 모두 보이며 문서·콘텐츠·그리드에 스크롤/잘림·HUD겹침이 없었다. 경고와 긴 물품명이 있는 공간 부족 상태도 확인했다.1280에서 이미지 높이는 일반106px·제한 상태90px 이상으로, 기존112×63 썸네일보다 크게 표시됐다. 사진은 현재 로컬SVG 임시 그림이며 실제 사진으로 바꿀 수 있다.

돌아가기 시 요청/시간 진행 없음, 진행 중 실제 병원 탐색에서 복귀 후 응답이 와도 재진입/중복 없음, 메인 새게임 버튼의 실제 클릭 영역, 안전지대의 기존 X 모달 유지, 런타임 예외0을 확인했다. 투명한 전체 화면 컨테이너가 새게임 클릭을 가리는 문제를 브라우저에서 재현한 뒤 포인터 영역을 카드·상단 정보로 제한해 수정했다.

증거: work/fullpage-java.log, fullpage-js.log, fullpage-browser-final.log, fullpage-browser-result.json, fullpage-1280/1440/1920.png. 검증은 별도DB에서 이번 판만 사용·정리했고 검증서버/Chrome은 종료했다. 실제 팀원 모듈·방 상세·최종 사진 작업은 아직 남아 있다.

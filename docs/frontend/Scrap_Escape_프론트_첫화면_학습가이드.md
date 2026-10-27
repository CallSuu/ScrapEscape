# Scrap Escape 프론트엔드 첫 단계

기준일:2026-10-27. 담당 A의 첫 단계는 ‘시작 화면 → 실제 새 게임 생성 → 메인/HUD 표시’다. 탐색 카드·비밀의 방은 다음 단계, 안전지대·결과는 팀원 B의 모듈로 연결한다. 전체 프론트 완성을 뜻하지 않는다.

## 1. 지금 볼 화면

1. IntelliJ에서 이 프로젝트를 연다. Project SDK/Gradle JVM은 기존처럼 Java17이다.
2. MySQL을 실행한 상태에서 `ScrapEscapeApplication`을 실행한다. 이전 실행이 떠 있으면 중지 후 재실행한다. 실행 작업 디렉터리는 저장소 루트다.
3. 브라우저에서 `http://localhost:8080`을 연다. HTML 파일을 Finder에서 더블클릭해 열지 않는다.
4. 처음에는 ‘새 게임 시작’ 버튼만 보인다. 버튼을 누르면 DB에 새 판이 생기고 체력10·시간1일08:00·세척액1/5·인벤토리0/4가 표시된다.
5. 안전지대/탐색을 누르면 이번 단계에서는 준비 중 안내 창이 나온다. X/Escape로 닫을 수 있다. 다음 모듈을 붙일 자리이며 실제 재료 탐색이나 수리는 아직 UI에서 실행되지 않는다.

기본 포트가8080이 아니면 설정한 포트로 연다. 서버가 실행되지 않으면 화면/API를 사용할 수 없다. 새로고침 시에는 이어하기 대신 시작 화면으로 돌아간다. 자동으로 새 게임 POST를 보내지 않는다.

## 2. HTML·CSS·JavaScript의 역할

| 종류 | 현재 파일 | 담당하는 일 |
|---|---|---|
| HTML | [index.html](../../src/main/resources/static/index.html) | 앱·메인·메시지·선택창·닫기 버튼의 큰 구조 |
| CSS | [common.css](../../src/main/resources/static/css/common.css) | 공통 색상·기본 버튼·선택창·메시지 위치 |
| CSS | [main.css](../../src/main/resources/static/css/main.css) | 메인 배경·시작 화면·HUD·장소 버튼 모양과 위치 |
| JavaScript | [app.mjs](../../src/main/resources/static/js/app.mjs) | 서버 요청·현재 상태·열린 창·공통 오류/재시도 연결 |
| JavaScript | [main.mjs](../../src/main/resources/static/js/main.mjs) | 시작/HUD의 내부 HTML 생성·버튼 클릭·화면 값 갱신 |
| JavaScript | [game-api.mjs](../../src/main/resources/static/js/game-api.mjs) | 기존 공통 fetch·UUID 요청 번호·재전송 방지 |
| SVG | [world.svg](../../src/main/resources/static/assets/main/world.svg) | 직접 작성한 교체 가능한 숲·비행기·도시 배경 |

`.mjs`는 이 프로젝트에서 JavaScript 코드를 파일별로 나누어 가져오기 위한 확장자다. `index.html`의 `type="module"`이 app.mjs를 시작점으로 읽는다. Java/Spring Boot가 화면 파일과 API를 같은 주소에서 제공한다. Node 서버를 새로 띄우지 않는다.

현재 배경은 로컬 SVG 임시 디자인이다. 그림을 교체해도 버튼과 HUD의 데이터 연결은 유지할 수 있도록 분리했다. 최종 이미지나 효과를 먼저 완성한 상태는 아니다.

## 3. 코드 읽는 순서

처음부터 모든 코드를 외우지 않아도 된다. 다음 네 곳만 먼저 확인한다.

### 3.1. index.html: 화면을 놓을 자리

```html
<main id="se-main"></main>
```

`main`은 주요 화면 영역이고, `id="se-main"`은 JavaScript가 이 영역을 찾기 위한 이름이다. 이 파일에는 큰 틀만 있으며 시작 버튼/HUD의 내부 HTML은 `main.mjs`에서 만든다.

### 3.2. main.mjs: 시작 버튼 만들기

```html
<button type="button" data-action="start">새 게임 시작</button>
```

이처럼 HTML은 무엇을 표시할지 정한다. `data-action="start"`는 클릭했을 때 어떤 행동인지 구분하는 표식이다. `root.innerHTML`에는 개발자가 정한 고정 HTML만 넣고, 서버에서 받은 재료 이름/메시지 등은 `textContent`로 넣는다.

### 3.3. main.css: 위치와 모양

```css
.se-main__clock {
  left: 24px;
  top: 20px;
  width: 360px;
}
```

`.se-main__clock`은 이 class를 가진 시간 영역을 선택한다. 부모 기준 왼쪽24px·위20px에 배치하고 너비360px을 주는 부분이다. 그림·HUD·중앙 창이 서로 겹치지 않는지는 실제 해상도에서 확인한다.

### 3.4. main.mjs: 실제 값을 넣기

```javascript
get('stamina').textContent = s.stamina + ' / ' + s.maxStamina;
```

`s`는 서버에서 온 현재 게임 상태다. 체력이 바뀌면 서버의 새 값을 이 영역에 표시한다. 화면에서 `s.stamina--` 같은 방식으로 직접 체력을 깎지 않는다.

## 4. 버튼 한 번의 흐름

```text
새 게임 시작 클릭
  → main.mjs가 START_GAME 명령 전달
  → app.mjs가 공통 game-api.mjs 호출
  → POST /api/games
  → Spring Boot가 MySQL에 새 게임 생성
  → GET /api/games/{id}/screen
  → onSnapshot으로 최신 상태 수신
  → mainView.render(screen, flags)
  → 체력·시간·물자·인벤토리 표시
```

‘렌더링(render)’은 받은 데이터로 화면을 그리거나 갱신한다는 뜻이다. 이번 코드에서 render 자체는 서버 요청을 보내지 않는다. 그래서 화면이 여러 번 갱신돼도 같은 행동이 여러 번 실행되지 않는다.

`busy`는 요청 중, `hasPending`은 결과 확인이 필요한 요청이 남아 있다는 뜻이다. 새 행동은 잠시 막고, 응답 유실이면 같은 요청 번호로 재시도한다. 최신 screen을 읽기 전까지 과거 응답을 최종 화면으로 사용하지 않는다.

## 5. 작은 수정으로 익혀보기

다음은 직접 시도해도 데이터 처리에 영향을 주지 않는 예다. 한 번에 한 항목만 바꿔 차이를 확인한다.

1. main.mjs의 시작 화면 설명 문장을 바꾼다.
2. main.css의 `.se-main__start` 배경색을 바꾼다.
3. main.css의 `.se-main__brand` 위치를 조금 바꿔본다.
4. 저장하고 서버의 정적 파일이 다시 반영되게 재실행한 뒤 브라우저를 새로고침한다. 계속 옛 화면이면 강력 새로고침도 확인한다.

IntelliJ 실행 시 정적 리소스 복사/빌드 상태에 따라 저장만으로 바로 반영되지 않을 수 있다. 먼저 재실행해 확인한다. 브라우저 개발자 도구에서 바꾼 CSS는 원본 파일에 자동 저장되는 것이 아니다.

게임 규칙·API 경로·분업 factory 이름은 연습 삼아 바꾸지 않는다. 팀원과 연결할 약속이므로 변경 전 합의가 필요하다.

## 6. 검증 기록과 현재 한계

- 기존 MySQL 전체115개: 실패0·오류0·건너뜀0. JAR 빌드 성공.
- JavaScript25개: 기존11개+app 호스트14개. 전체 통과.
- 실제 Chrome: 새로 열기 자동POST 없음, 시작 연속 클릭 시 새 게임 POST1회, 실제 DB 응답 HUD 표시.
-1280×720·1440×900·1920×1080에서 HUD 세 영역의 화면 내 배치·상호 겹침 없음·가로 넘침 없음 확인.
- 선택창 준비 안내·X 후 진입 버튼 포커스 복귀·새로고침 시 새 판 자동 생성 안 됨 확인.
- 전용 테스트 판에 조건을 준비해 밤·질병 치료 잔여71h·미세척 잔여5h 위험 표시를 확인했다. 개인 게임 DB는 변경하지 않았다.
- 브라우저에서 잡히지 않은 런타임 예외0. 아직 없는 탐색/팀원 모듈을 열면 이 단계에서는 해당 import404와 준비 안내가 예상된다. 모든 프론트 리소스 통합이 끝났다는 뜻은 아니다.
- 전체 키보드/스크린리더 접근성, 실제 팀원 모듈·탐색/방 UI·플레이 밸런스는 다음 단계다. 검증용 서버와 Chrome 프로필은 종료하고 이번 테스트 판만 정리했다.

다음 수업 단위는 `exploration.mjs`·`exploration.css`를 추가해 **탐색 버튼 → 실제 장소6개 카드 → 탐색 결과 → HUD 갱신**을 연결하는 것이다. 이후 비밀의 방을 연결하고 팀원의 모듈을 합친다.

## 새 게임 버튼의 두 단계

메인의 ‘새 게임’을 누르면 진행 중일 때 확인 후 처음 시작 화면으로 돌아간다. 이때는 서버 요청 없이 현재 화면 상태·창·메시지·밤 테마를 비운다. 시작 화면에서 ‘새 게임 시작’을 눌러야 새 게임 생성POST를 보낸다. 응답이 불확실한 요청이 있으면 먼저 재시도로 확인해야 복귀할 수 있다. 기존 판 삭제 정책은 바꾸지 않는다.

새 게임 복귀 검증: Node 전체25개 통과, 실제 Chrome에서 확인 취소/복귀 시POST0회/중복 새게임 클릭/밤·메시지 초기화/다음 시작POST1회·서로다른UUID 확인. 전체MySQL115개 재실행·JAR빌드 통과. 증거는 work/welcome-final-js.log·welcome-java.log·welcome-browser-result.json. 검증은 전용DB에서 이번에 생성한 판만 사용·정리했다.

이후 진행: [두 번째 단계 탐색 화면](Scrap_Escape_탐색화면_학습가이드.md)에6개 카드·탐색 요청·획득 결과 연결을 구현했다. 첫 단계의 ‘탐색 준비 중’ 설명은 당시 상태이며 현재 일반 탐색은 실행 가능하다.

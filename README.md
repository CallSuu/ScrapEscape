# Scrap Escape

**오염된 도시를 탐색하고, 수집한 재료로 비행기를 수리해 10일 안에 탈출하는 생존 게임**입니다.

데이터베이스 캡스톤디자인 프로젝트로 제작했습니다. 버튼으로 행동할 때만 시간이 흐르며, 수집·보관·세척·수리·폐기를 통해 CRUD와 트랜잭션을 확인할 수 있습니다.

![숲속 비행기와 오염 도시](src/main/resources/static/assets/main/main-landscape.png)

## 구현된 기능

- 시작 화면, 메인 HUD, 숲속 안전지대, 오염 도시의 탐색 장소 6곳
- 장소별 수색 정보, 재료 후보 아이콘, 클릭해서 여는 탐색 안내
- 아이템 카드로 보여주는 탐색 결과와 장소·결과 전용 배경
- 인벤토리·보관함의 다중 선택, 일괄 이동·세척·폐기
- 휴식·오염도·질병·병원 치료·방사능 폭풍
- 비행기 도면의 부위별 수리·장착과 직접 선택하는 탈출
- 비밀 열쇠, 방 발견·기억·선택 입장·재방문, 좋은 결과와 나쁜 결과
- 성공·실패 엔딩 이미지와 최종 통계 21개
- 서버 검증에 따른 실행 제한·사유 안내, 중복 클릭 방지와 미확정 요청 재시도

기본 게임 흐름과 디자인을 구현했습니다. **모든 무작위 조합의 난이도와 장시간 플레이 균형은 추가 플레이로 조정할 수 있습니다.** 로그인·이어하기·순위표·외부 서비스 배포는 포함하지 않습니다.

## 게임 진행

1. 새 게임을 시작하고 무작위로 지정된 고장 부품 4개를 확인합니다.
2. 도시에서 탐색 장소를 선택해 재료와 보급품을 얻습니다.
3. 안전지대에서 재료를 상자에 보관하거나 세척합니다. 필요 없는 재료는 폐기합니다.
4. 체력이 부족하면 휴식하고, 질병이 발생하면 의료 키트로 치료합니다.
5. 필요한 세척 재료를 모아 부품을 수리·장착합니다.
6. 모든 부품을 수리한 뒤 **탈출하기**를 누르면 성공합니다.

새 게임 버튼은 시작 화면으로 돌아가는 동작이며, 이후 새 게임 시작을 눌러 새 판을 만듭니다. 새로고침하면 이어하기 없이 시작 화면으로 돌아갑니다. 이전 판의 DB 기록을 자동 삭제하는 기능은 없습니다.

### 주요 규칙

| 항목 | 현재 규칙 |
| --- | --- |
| 생존 기한 | 게임 시간 240시간(10일) |
| 체력 | 시작·최대 15 |
| 탐색 | 낮 6시간/체력2, 밤 7시간/체력3. 밤은20:00~06:00 |
| 재료 | 10종, 탐색당 최대2종. 낮 총0~3개/밤 총0~4개 |
| 저장 공간 | 인벤토리4칸, 비행기 보관함16칸, 한 묶음 최대6개 |
| 재료 만료 | 미세척 상태로48시간 경과하면 사용 불가. 직접 폐기 필요 |
| 세척 | 재료1개당 세척액1회분, 묶음당1시간, 체력 소비 없음 |
| 세척액 | 시작1/최대10. 일반 탐색마다1회분70%·2회분30% 지급, 여유 용량까지만 저장 |
| 휴식 | 1~8시간 선택, 시간만큼 체력 회복·6시간 이상이면+2, 상한15 |
| 오염 회복 | 휴식 시간당5 감소,4시간 이상 한 번에 쉬면 연속 탐색 초기화 |
| 질병 | 탐색 시간·체력 각각+2. 발병 후72시간 안에 치료하지 않으면 실패 |
| 의료 키트 | 최대2개, 병원에서1개 확정. 치료1시간·오염−60·질병 제거 |
| 수리 | 7종 중 무작위4종, 조합별 재료 합계7개, 수리·장착3시간 |
| 열쇠·방 | 일반 탐색에서 열쇠10%/미발견 장소의 방15%, 열쇠 최대2개 |

세척액이 선택한 재료 총수량보다 적으면 **전체 세척을 차단**합니다. 세척한 같은 재료는 같은 저장소에서 최대6개씩 합칩니다. 미세척 묶음은 획득 시점을 유지합니다. 보관함·수리 작업은 완료 후 창을 유지하고, 휴식·치료는 야영지로 복귀합니다.

전체 확률·조합법·폭풍·비밀의 방 규칙은 [게임 기획안](docs/game/Scrap_Escape_게임_기획안.md)을 참고하세요.

## 기술 구성

| 영역 | 사용 기술 |
| --- | --- |
| 백엔드 | Java17, Spring Boot3.5.16, JdbcTemplate |
| 데이터베이스 | MySQL, Flyway 마이그레이션V1~V10 |
| 프론트엔드 | HTML, CSS, JavaScript ES Modules |
| 빌드 | Gradle Wrapper8.14.3 |
| 테스트 | JUnit/Spring Boot Test, 실제 MySQL 통합 테스트, Node.js 기본 테스트 러너 |

Spring Boot가 프론트 파일과 API를 함께 제공합니다. 별도 프론트 서버나 npm 빌드가 필요하지 않습니다. Node.js는 클라이언트 테스트에만 사용합니다.

## 로컬 실행

### 1. 준비

Java17과 MySQL을 설치하고 MySQL 서버를 실행합니다. MySQL은 CHECK 제약을 지원하는8.0.16 이상을 사용하세요.

```bash
git clone https://github.com/CallSuu/ScrapEscape.git
cd ScrapEscape
```

MySQL에 접속해 전용 DB를 생성합니다.

```sql
CREATE DATABASE IF NOT EXISTS scrap_escape
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. DB 연결 설정

프로젝트 루트의 예시 파일을 복사합니다.

```bash
cp application-local.properties.example application-local.properties
```

복사한 파일에 본인의 계정과 비밀번호를 입력합니다.

```properties
spring.datasource.username=root
spring.datasource.password=본인_MySQL_비밀번호
```

`application-local.properties`는 Git에서 제외됩니다. 실제 비밀번호를 커밋하지 마세요. 역슬래시 등 특수 문자는 Java properties 형식에 맞게 이스케이프해야 합니다.

기본 DB 주소는 `jdbc:mysql://127.0.0.1:3306/scrap_escape`입니다. 환경에 따라 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `PORT`로 변경할 수 있습니다. 로컬 파일과 환경변수에 같은 설정을 중복 지정하지 마세요.

### 3. 서버 시작

```bash
./gradlew bootRun
```

브라우저에서 **[http://localhost:8080](http://localhost:8080)**을 엽니다. IntelliJ에서는 Gradle 프로젝트로 불러오고 Project SDK·Gradle JVM을 Java17로 지정한 뒤 `ScrapEscapeApplication`을 실행하면 됩니다. 실행 작업 디렉터리는 프로젝트 루트로 설정하세요.

macOS에서 Homebrew Java17을 사용하는 경우 다음과 같이 설정할 수 있습니다.

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
./gradlew bootRun
```

배포용 실행 파일을 만드는 방법:

```bash
./gradlew bootJar
java -jar build/libs/scrap-escape-0.1.0.jar
```

처음 실행하면 Flyway가 스키마와 초기 데이터를 생성합니다. 기존 DB는 미적용 마이그레이션만 실행합니다. V9는 세척액 한도를10으로 확장하고, V10은 진행 중 확장 규칙 판의 미수리 조합만 최신7개 규칙으로 변경합니다. 완료된 수리·종료된 판의 조합 기록은 보존합니다. 마이그레이션 파일을 임의로 수정하거나 이력 테이블을 삭제하지 마세요.

화면 수정 후 이전 이미지가 보이면 `⌘+Shift+R`(Windows/Linux는 `Ctrl+Shift+R`)로 강력 새로고침하세요.

## 데이터베이스와 CRUD

| 행동 | DB 처리 |
| --- | --- |
| 새 게임·재료 수집 | 게임·수리 대상·재료 묶음 INSERT |
| HUD·재료·조합 조회 | 게임 상태·재료·조합·행동 미리보기 SELECT |
| 보관함 이동 | 재료 보관 위치 UPDATE, 세척 재료 병합 시 수량 UPDATE/빈 행 DELETE |
| 세척·휴식·치료 | 재료 상태·자원·시간·오염·질병 UPDATE |
| 수리·장착 | 재료 일부 소비 UPDATE/전량 소비 DELETE, 수리 완료 UPDATE |
| 폐기 | 선택 재료 묶음 DELETE, 폐기 통계 UPDATE |

같은 판의 변경은 게임 행을 `FOR UPDATE`로 잠그고 트랜잭션 안에서 처리합니다. 여러 묶음을 처리하다 오류가 나면 전체를 취소합니다. 생성 포함 변경14개 API는 `Idempotency-Key`를 지원합니다. 프론트는 요청마다 UUID를 만들고 네트워크 재시도에는 같은 키를 재사용해 중복 소비를 방지합니다.

## 프로젝트 구조

```text
src/main/java/com/scrapescape/
  game/                  게임 API·규칙·SQL·미리보기·비밀의 방
src/main/resources/
  application.yml        기본 실행 설정
  db/migration/          Flyway 스키마·초기 데이터·업그레이드
  static/
    index.html           게임 진입점
    js/                  화면 모듈·공통 API 클라이언트
    css/                 공통 스타일·화면별 디자인
    assets/              배경·아이콘·비행기 도면
src/test/                Java·JavaScript 테스트
docs/                    기획·API·개발·디자인 문서
```

## 테스트와 검증

JavaScript 테스트(Node.js 필요):

```bash
node --test src/test/javascript/*.test.mjs
```

기본 Java 테스트:

```bash
./gradlew test
```

실제 DB 통합 테스트는 개인 게임 DB와 분리된 전용 DB가 필요합니다.

```sql
CREATE DATABASE IF NOT EXISTS scrap_escape_test
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

```bash
MYSQL_TEST_URL='jdbc:mysql://127.0.0.1:3306/scrap_escape_test?serverTimezone=Asia/Seoul' \
  ./gradlew test --rerun-tasks bootJar
```

테스트 계정에는 테스트 DB의 테이블·트리거 생성/삭제 권한이 필요합니다. 마이그레이션 검증은 임의 이름의 `scrap_escape_test_migration_*` DB를 생성·삭제하므로 테스트 스키마 생성 권한도 필요합니다. `MYSQL_TEST_URL`이 없으면 DB 테스트는 건너뜁니다.

현재 검증: **Java/MySQL134개·JavaScript75개**, 실행 파일 빌드. Chrome에서1280×720·1440×900·1920×1080 화면 배치, 결과·작업 창·탐색 안내와 아이콘을 점검했습니다. 이는 모든 플레이 조합의 난이도나 모든 브라우저 지원을 보장하는 수치는 아닙니다.

## 문서

- [문서 전체 안내](docs/README.md)
- [게임 기획안·전체 확률·조합법](docs/game/Scrap_Escape_게임_기획안.md)
- [API 명세서 —19개 API](docs/api/Scrap_Escape_API_명세서.md)
- [OpenAPI3.1](docs/api/openapi.json) · [실제 응답 예시](docs/api/examples/)
- [API 실행 가이드](docs/api/Scrap_Escape_API_실행가이드.md)
- [백엔드 구현·검증 현황](docs/backend/Scrap_Escape_백엔드_구현현황.md)
- [화면 연동 명세](docs/frontend/Scrap_Escape_화면_연동_명세.md)
- [디자인 통합 점검](docs/frontend/Scrap_Escape_디자인_통합점검.md)
- [아이콘 수정 가이드](docs/frontend/Scrap_Escape_아이템_아이콘_가이드.md)

배경 이미지는 AI 이미지 생성 도구로 제작했으며, 아이콘과 항공기 도면은 SVG로 구성했습니다. 기능·데이터 처리·디자인을 로컬 PC에서 시연하는 수업용 프로젝트입니다.

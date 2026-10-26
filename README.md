# Scrap Escape

오염된 도시에서 재료를 수집·세척·조합하여 10일 안에 비행기를 수리하는 DB 캡스톤 게임의 백엔드입니다.

## 개발 환경

- Java 17 / Spring Boot 3.5.16
- Gradle Wrapper 8.14.3
- MySQL / JdbcTemplate / Flyway
- IntelliJ IDEA

로그인·이어하기 없이 새 판마다 UUID를 생성합니다. 현재는 API와 DB 기능을 제공하며 게임 화면은 다음 단계에서 연결합니다.

## 처음 실행

1. MySQL에서 전용 DB를 만듭니다. 이미 만들었다면 생략합니다.

```sql
CREATE DATABASE IF NOT EXISTS scrap_escape CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. 프로젝트 루트에 `application-local.properties`를 만듭니다. 예시는 `application-local.properties.example`입니다.

```properties
spring.datasource.username=root
spring.datasource.password=본인MySQL비밀번호
```

이 파일은 Git에서 제외됩니다. 비밀번호의 역슬래시는 Java properties 규칙에 따라 이스케이프해야 합니다. 환경변수로 설정할 경우 로컬 파일의 같은 설정을 제거하고 `DB_USERNAME`, `DB_PASSWORD`를 사용하세요. 외부에서 받은 샘플·문서나 비밀번호는 소스에 추가하지 마세요.

3. IntelliJ에서 이 폴더의 `build.gradle`을 Gradle 프로젝트로 불러옵니다.

- Project SDK와 Gradle JVM을 Java 17로 설정합니다.
- 이 Mac의 JDK 경로: `/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`
- `ScrapEscapeApplication`의 main을 실행합니다.
- 실행 작업 디렉터리는 프로젝트 루트로 설정합니다. 로컬 properties 파일은 이 디렉터리를 기준으로 읽습니다.

터미널에서도 실행할 수 있습니다.

```bash
cd /Users/suhyeokyun/IdeaProjects/ScrapEscape
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export GRADLE_USER_HOME="$PWD/work/gradle-home"
./gradlew bootRun
```

서버 주소는 `http://localhost:8080`입니다. 현재 `/` 게임 화면은 없으므로 Postman이나 curl로 API를 호출합니다. 실행 시 Flyway가 `db/migration`의 변경 이력을 적용합니다. 빈 DB는 V1부터, 초기 버전 DB는 V1 기준점을 기록한 뒤 확장 마이그레이션을 적용합니다. 재시작 시 데이터와 초기 설정을 다시 덮어쓰지 않습니다. 기존 판의 재료·시간·조합법·이름은 보존하지만 초기 규칙 판은 조회만 허용하므로 새 게임을 시작하세요. 6개를 넘던 기존 묶음은 수량·상태·획득 시각을 보존해 나눕니다. 새로고침 후 새 판을 시작하는 동작은 이후 화면에서 구현합니다.

## API와 구조

[전체 개발 문서](docs/README.md)

[API 실행 가이드](docs/api/Scrap_Escape_API_실행가이드.md)

- [전체 API 명세서:15개 API](docs/api/Scrap_Escape_API_명세서.md)
- [기획 대비 백엔드 구현 현황·미완성 항목](docs/backend/Scrap_Escape_백엔드_구현현황.md)
- [OpenAPI3.1 JSON](docs/api/openapi.json)
- [실제 요청·응답 예시](docs/api/examples)

- `GameController`: URL과 요청 입력
- `GameService`: 게임 규칙, 트랜잭션, 시간·엔딩 처리
- `GameRepository`: SQL과 게임별 행 잠금
- `GameModels`: 요청·응답 형식
- `GameRules`: 낮·밤, 획득 수량, 휴식 회복량
- `ApiExceptionHandler`: 오류 응답
- `db/migration`: 버전별 스키마·고정 데이터 변경 및 기존 묶음 분할

상태 변경은 동일 게임의 행을 `FOR UPDATE`로 잠그고 처리합니다. 부분 재료 소비는 UPDATE, 전량 소비 및 폐기는 DELETE입니다. 묶음은 최대 6개이며 같은 위치의 세척 완료 재료만 6개씩 합칩니다. 미세척 묶음은 획득 시점을 따로 유지합니다.

## 테스트

일반 규칙 테스트:

```bash
./gradlew test
```

실제 MySQL 통합 테스트는 별도 DB를 사용합니다.

```sql
CREATE DATABASE IF NOT EXISTS scrap_escape_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

```bash
MYSQL_TEST_URL='jdbc:mysql://127.0.0.1:3306/scrap_escape_test?serverTimezone=Asia/Seoul' ./gradlew test --rerun-tasks
```

통합 테스트 계정은 전용 테스트 DB의 테이블·트리거 생성/삭제 권한이 필요합니다. 마이그레이션 테스트는 임의 이름의 `scrap_escape_test_migration_*` DB를 생성하고 해당 DB만 삭제하므로 테스트 스키마 생성 권한도 필요합니다. 테스트는 자신이 생성한 게임을 정리하며, 실제 게임 DB에서는 실행하지 않습니다. `MYSQL_TEST_URL`을 지정하지 않으면 MySQL 테스트는 건너뜁니다. 기본 규칙 테스트 통과만으로 DB 연동을 확인했다고 판단하면 안 됩니다.

실행 중인 HTTP 서버의 CRUD·실패 엔딩 시연:

```bash
python3 scripts/smoke.py
```

이 시연은 새 게임을 만들고 해당 게임을 DB에 남깁니다. 기존 게임은 수정하지 않습니다. 성공 엔딩, 트랜잭션 롤백, 동시 요청 및 경계값은 MySQL 통합 테스트에서 확인합니다.

## 로컬 개발 범위

계정 인증과 외부 배포 설정은 포함하지 않습니다. 무작위 UUID로 판을 구분하며, 프로젝트는 개인 PC 시연용입니다. 중복 수리·슬롯 초과 등 동시 변경은 DB 잠금으로 막습니다. 생성을 포함한 변경11개 API에 Idempotency-Key UUID를 전달하면 같은 요청의 재전송은 최초 응답을 재사용합니다. 헤더가 없으면 별도 행동입니다. 프론트는 처리 중 버튼 비활성화와 함께 행동별 UUID를 관리하고, 재사용 응답 이후 최신 상태를 다시 조회합니다. Flyway V6에서 api_request 테이블을 추가합니다.

## 확장된 게임 규칙

- 장소6곳·재료10종·조합법7개 중 무작위4개 수리, 제한240시간
- 인벤토리4칸·보관함16칸·묶음6개, 탐색당 최대2종류. 공간 초과분은 결과로 안내하고 통계에서 제외
- 오염도·질병·병원 의료 키트·72시간 치료 기한, 키트 사용은 질병 제거와 오염도60 감소
- 폭풍24~48시간 간격·1~3시간 지속, 실제 탐색 노출 시간당 오염+5
- 모든 부품 수리 후 `POST /api/games/{id}/escape`로 직접 탈출. 수리만으로 자동 엔딩되지 않음
- `POST /api/games/{id}/treatments`로 의료 키트 사용. 탐색 카드에 비용·오염·실행 제한 이유 제공

비밀의 열쇠·방 백엔드도 구현되어 있습니다. 프론트엔드는 아직 포함하지 않습니다. UI에서는 미세척 재료의 `expiresInHours`로 아이콘 위 잔여 시간과12/6시간 경고를 표시할 수 있습니다.

DB 초기화는 Spring Boot 공식 Flyway 통합 방식을 사용합니다: https://docs.spring.io/spring-boot/how-to/data-initialization.html

## 비밀의 열쇠·방 API

- 일반 탐색 완료 후 열쇠10%·해당 장소 미발견 방15%를 독립 판정합니다. 열쇠는 별도 최대2개이며 발견한 방은 자동으로 열지 않습니다.
- `GET /api/games/{id}/secret-rooms`와 `GET /api/games/{id}/secret-rooms/{locationId}`로 기억한 방과 입장 비용을 조회합니다.
- `POST /api/games/{id}/secret-rooms/{locationId}/open`에 `{"mode":"IMMEDIATE"}` 또는 `{"mode":"REVISIT"}`를 보냅니다.
- 미개방 방 선택창의 X·지나치기는 `POST /api/games/{id}/secret-rooms/{locationId}/defer`로 처리합니다. 시간·열쇠를 소비하지 않습니다.
- 즉시 입장은1시간·체력1·열쇠1이며 발견 후 다른 유효 행동을 하면 종료됩니다. 재방문은 낮4시간·체력2,밤5시간·체력3이며 질병이면 각각+2입니다.
- 장소당1개·방당1회. 좋은 결과80%·나쁜 결과20%, 열쇠와 방 통계는 `state.secrets.statistics`에 포함합니다.
- V5 마이그레이션은 기존 판의 진행을 초기화하지 않고 열쇠0과 빈 방 기억 상태를 추가합니다.

[비밀의 방 구현·검증 기록](docs/backend/Scrap_Escape_비밀의방_구현_검증.md)

## 화면 연동 준비

`GET /api/games/{id}/screen`: 기존 상태·탐색 카드·안전지대 행동 미리보기를 한 번에 조회합니다. [화면 연동 명세](docs/frontend/Scrap_Escape_화면_연동_명세.md)에 화면 위치별 필드와 버튼 제한을 정리했습니다. `/js/game-api.mjs`는 행동별 UUID·동시 클릭 차단·수동 재시도·오류/상태 콜백을 제공하는 공통 모듈이며, 실제 화면 디자인은 다음 단계입니다.

클라이언트 테스트: `node --test src/test/javascript/*.test.mjs` (Node는 테스트 도구로만 사용).

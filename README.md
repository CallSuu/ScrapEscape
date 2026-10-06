# Scrap Escape

오염된 도시에서 재료를 수집·세척·조합하여 7일 안에 비행기를 수리하는 DB 캡스톤 게임의 백엔드입니다.

## 개발 환경

- Java 17 / Spring Boot 3.5.16
- Gradle Wrapper 8.14.3
- MySQL / JdbcTemplate
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

서버 주소는 `http://localhost:8080`입니다. 현재 `/` 게임 화면은 없으므로 Postman이나 curl로 API를 호출합니다. 실행 시 `schema.sql`과 `data.sql`이 테이블 7개와 고정 재료·조합법을 등록합니다. 재시작할 때 기존 게임 데이터는 삭제하지 않습니다. 새로고침 후 새 판을 시작하는 동작은 이후 화면에서 구현합니다.

## API와 구조

[API 실행 가이드](outputs/Scrap_Escape_API_실행가이드.md)

- `GameController`: URL과 요청 입력
- `GameService`: 게임 규칙, 트랜잭션, 시간·엔딩 처리
- `GameRepository`: SQL과 게임별 행 잠금
- `GameModels`: 요청·응답 형식
- `GameRules`: 낮·밤, 획득 수량, 휴식 회복량
- `ApiExceptionHandler`: 오류 응답
- `schema.sql` / `data.sql`: DB 구조와 고정 데이터

상태 변경은 동일 게임의 행을 `FOR UPDATE`로 잠그고 처리합니다. 부분 재료 소비는 UPDATE, 전량 소비 및 폐기는 DELETE입니다. 같은 위치의 세척 완료 재료만 합치며, 미세척 묶음은 획득 시점을 따로 유지합니다.

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

통합 테스트 계정은 테스트 DB에서 테이블·트리거 생성/삭제 권한이 필요합니다. 테스트는 자신이 생성한 게임을 정리하며, 실제 게임 DB에서는 실행하지 않습니다. `MYSQL_TEST_URL`을 지정하지 않으면 MySQL 테스트는 건너뜁니다. 기본 규칙 테스트 통과만으로 DB 연동을 확인했다고 판단하면 안 됩니다.

실행 중인 HTTP 서버의 CRUD·실패 엔딩 시연:

```bash
python3 scripts/smoke.py
```

이 시연은 새 게임을 만들고 해당 게임을 DB에 남깁니다. 기존 게임은 수정하지 않습니다. 성공 엔딩, 트랜잭션 롤백, 동시 요청 및 경계값은 MySQL 통합 테스트에서 확인합니다.

## 로컬 개발 범위

계정 인증과 외부 배포 설정은 포함하지 않습니다. 무작위 UUID로 판을 구분하며, 프로젝트는 개인 PC 시연용입니다. 중복 수리·슬롯 초과 등 동시 변경은 DB 잠금으로 막습니다. 같은 유효 탐색 요청을 다시 전송하면 별도 행동으로 처리되므로 프론트는 처리 중 버튼을 비활성화해야 합니다.

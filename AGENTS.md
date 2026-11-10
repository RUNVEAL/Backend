# AGENTS.md

러닝 코스 앱 백엔드. 작업 전 이 문서를 읽고 규칙을 따른다. (CLAUDE.md도 이 파일을 가리킨다.)

## 프로젝트 개요
- 러닝 코스 공유 앱의 백엔드 API. **소셜 로그인 전용**(카카오·구글 등 OAuth2/OIDC) → 백엔드가 provider 토큰 검증 후 **자체 JWT 발급** 예정.

## 기술 스택
- Java 21 (가상 스레드), Spring Boot 4.1.1
- PostgreSQL (운영·로컬 개발), H2 인메모리 (테스트)
- Spring Data JPA, Spring Security, springdoc-openapi
- 빌드: Gradle (wrapper), base package `com.runmap.api`

## 명령어
| 목적 | 명령 |
|------|------|
| 빌드 | `./gradlew build` |
| 테스트 | `./gradlew test` — H2 인메모리, **Docker 불필요** |
| 앱 실행 | `./gradlew bootRun` — **로컬 PostgreSQL 필요** |
| 포맷 정리 | `./gradlew spotlessApply` |
| 규칙 검사 | `./gradlew checkstyleMain checkstyleTest` |

### 로컬 DB (앱 실행용)
1. PostgreSQL 설치·실행 (예: `brew install postgresql@16 && brew services start postgresql@16`)
2. DB 생성: `createdb runmap`
3. `src/main/resources/application-local.yml` 작성 (gitignore됨 — 각자 본인 DB 계정으로):
   ```yaml
   spring:
     datasource:
       url: jdbc:postgresql://localhost:5432/runmap
       username: <본인 DB 유저>
       password: <비번, 로컬 trust면 "">
   ```
- `./gradlew bootRun`은 **`local` 프로파일을 자동 활성화**해 `application-local.yml`을 읽음. (IntelliJ에서 main을 직접 실행하면 run config의 Active profiles에 `local` 지정)
- `ddl-auto`는 로컬 `update`. 운영 전 Flyway/Liquibase + `validate`로 전환할 것
- 테스트는 H2(`MODE=PostgreSQL`, `test` 프로파일) — Docker·DB 불필요

## 코드 스타일
- 포맷은 Spotless가 자동 정리 (설정은 `build.gradle`). 커밋 전 `./gradlew spotlessApply`
- 규칙은 Checkstyle (`config/checkstyle/checkstyle.xml`) — 네이밍·임포트·기본 품질. 위반 시 빌드 실패

## Git hooks (clone 후 한 번 활성화 필요)
```
git config core.hooksPath .githooks
```
- **pre-commit**: 스테이징된 자바 Spotless 포맷 + Checkstyle 검사
- **pre-push**: `./gradlew test` (H2라 Docker 불필요)
- **commit-msg**: 아래 커밋 규칙 검증

## 커밋 규칙 (Conventional Commits)
- 형식: `type(scope): subject` (**scope 필수** — 훅이 강제)
- type: `feat fix docs style refactor test chore perf ci`
- subject: 명령형, 마침표 없이, 이모지 없이. 본문 쓰면 제목과 빈 줄로 구분
- scope: 보통 도메인 패키지명(`user`, `course`, `auth`) / 비도메인은 `init`, `build`, `global` 등
- 예: `feat(user): 소셜 로그인 API 추가`

## 네이밍
- 패키지 소문자 / 클래스·enum PascalCase(+역할 접미사 `Controller`·`Service`·`Repository`) / 메서드 camelCase(동사) / 필드·변수·파라미터 camelCase / 상수 UPPER_SNAKE_CASE / boolean은 `is`·`has`·`can` / 제네릭 타입 파라미터 대문자 1글자
- DTO: `XxxCreateRequest`, `XxxUpdateRequest`, `XxxResponse` (방향·용도 드러나게)
- REST URL: `/api/v1/<복수명사>` kebab-case, 동사 금지 (행위는 HTTP 메서드로)
- DB: 테이블·컬럼 snake_case, 테이블은 복수형 (`users`)

## 패키지 구조 (도메인 분리 + 내부 계층형)
```
com.runmap.api
├── global/               # 공통
│   ├── config/           # SecurityConfig 등
│   ├── common/response/  # PageResponse
│   └── error/            # ErrorCode, BusinessException, GlobalExceptionHandler
└── <domain>/             # user, course ...
    └── controller/ service/ repository/ domain/ dto/
```

## 응답·예외
- 성공: DTO를 그대로 반환, 상태코드로 의미 표현 (200 / 201 Created / 204 No Content)
- 목록: `PageResponse<T>`로 감쌈
- 에러: `ProblemDetail` (RFC 9457, `application/problem+json`) + 비즈니스 `code` 확장 필드
- 던지기: `throw new BusinessException(ErrorCode.XXX)` → `GlobalExceptionHandler`가 변환
- 도메인마다 예외 클래스 새로 만들지 않음 (`ErrorCode` enum + `BusinessException` 하나로)

## 에러 코드
- `global/error/ErrorCode` enum 한 곳에서 `{HttpStatus, code, message}` 관리
- code = 도메인 접두어(영문 1~2자) + 3자리 (`C` 공통, `A` 인증, `U` 사용자, `CO` 코스 …)
- 예외: 지도 API(`/routes` · `/matches` · `/meta`)는 API 명세 §2.8의 문자열 코드(`INVALID_REQUEST`, `SERVER_BUSY` …)를 쓴다. 앱의 화면 분기 표가 이 값 기준이다
- 400은 `BusinessException.invalid(field, reason)` — 첫 위반 1건만, `reason`은 명세 §2.8 목록
- 배포된 code 값의 의미는 바꾸지 않음 (앱이 분기에 씀 = API 계약)
- 에러 `message`·로그에 입력값(좌표 등)을 넣지 않는다. 예외는 클래스 이름만 로그에 남긴다

## 브랜치 전략
- `main`: 운영 배포 기준 / `develop`: 기본·통합 브랜치
- 작업은 `develop`에서 `feature/*`·`fix/*` 분기 → PR 리뷰 후 develop 병합
- `hotfix/*`는 `main`에서 분기 → `main`·`develop` 양쪽 반영
- `main`·`develop` 직접 push 금지 (PR + 리뷰)

## 배포
- 운영 서버(Azure 서울 VM + Docker Compose)·지도 교체·롤백은 **[deploy/README.md](deploy/README.md)**. 위치 API 프라이버시 규칙(접속 로그 없음·힙 덤프 금지)도 거기 있다

## 비밀값
- OAuth 키·JWT 시크릿·DB 비밀번호 등은 **커밋 금지.** `application-local.yml` 또는 환경변수로 주입
- `.gitignore`가 `.env`·`application-local.yml`·`*.p8`/`*.key`/`*.pem` 등을 이미 제외함

## 보안 (현재 임시)
- `global/config/SecurityConfig`가 **전부 permitAll** (소셜 로그인 도입 전 임시). 인증 도입 시 실제 인가 규칙으로 교체

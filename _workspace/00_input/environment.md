# 개발 환경 — 리더가 확보한 사실 (2026-09-02)

에이전트는 이 파일을 신뢰하고 재조사하지 않는다. 여기 적힌 것과 다른 상황을 만나면 보고한다.

## 툴체인 (검증됨)

| 항목 | 값 | 비고 |
|---|---|---|
| Java | OpenJDK 21.0.11 (Temurin) | `java.toolchain 21` |
| Gradle | 8.14.3 (래퍼 생성 완료) | `backend/gradlew` 사용. 시스템 gradle CLI 없음 |
| Spring Boot | **3.5.3** | 아래 "스택 결정" 참조 |
| Node | v26.5.1 / npm 12.0.2 | |
| PostgreSQL | 서버 16.15 (Docker), 클라이언트 psql 17.10 | |
| Docker | 29.6.2 | |

## 로컬 데이터베이스 (기동 중)

기획서 11.2는 Supabase/Neon을 지정하지만 접속 정보가 없어 `data-architect` 에이전트 정의의 폴백 규칙대로 로컬 Docker로 대체했다.

```
컨테이너: gateway-pg (postgres:16-alpine)
호스트 포트: 55432 → 5432
DB / USER / PASSWORD: gateway / gateway / gateway
JDBC: jdbc:postgresql://localhost:55432/gateway
psql: docker exec gateway-pg psql -U gateway -d gateway -c "..."
```

클라우드 DB 접속 정보가 확보되면 환경변수 `DB_URL`·`DB_USER`·`DB_PASSWORD`만 바꾼다. 코드 변경은 없다(11.3 Config Isolation).

## 스택 결정 — Spring Boot 3.5.3

**배경:** start.spring.io가 현재 Boot 4.0.0 미만을 거부한다(`Spring Boot compatibility range is >=4.0.0`). 게다가 Boot 4 요청도 서버 측 BOM 해석 실패로 500을 반환해 Initializr 자체를 쓸 수 없었다.

**결정:** Gradle 8.14.3 배포본을 직접 받아 래퍼를 생성하고, `build.gradle`을 손으로 작성해 **Spring Boot 3.5.3**(Maven Central 최신 3.x)을 고정했다.

**근거:** 기획서 11.2의 "Spring Boot 3.3+, Java 21"을 문자 그대로 충족하면서 3.x 계열을 유지한다. Boot 4로 올리면 기획서 기술 스택 표와 어긋나고, 3일 스프린트에서 마이그레이션 이슈를 떠안는다. 팀원 4인이 참고할 문서·예제도 3.x가 압도적으로 많다.

**되돌리려면:** `backend/build.gradle`의 `org.springframework.boot` 버전 한 줄이다.

## 스캐폴딩 (완료, 검증됨)

```
backend/   gradlew + build.gradle(Boot 3.5.3, web/jpa/validation/flyway/postgresql/springdoc)
           GatewayApplication.java
           → ./gradlew compileJava BUILD SUCCESSFUL 확인
frontend/  Vue 3 + Vite (npm create vite --template vue)
           axios · pinia · vue-router 설치 완료
```

기획서 11.4의 폴더 구조는 아직 만들지 않았다. 각 담당 에이전트가 자기 패키지를 만든다.

## 주의

- `backend/`에서 Gradle 명령은 반드시 `./gradlew`를 쓴다. 시스템 gradle이 없다
- Flyway는 `flyway-core` + `flyway-database-postgresql` 두 개가 모두 필요하다 (Boot 3.5 기준)
- 첫 `./gradlew` 실행은 의존성 다운로드로 수 분이 걸릴 수 있다

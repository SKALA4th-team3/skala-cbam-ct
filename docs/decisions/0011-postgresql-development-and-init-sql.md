# ADR-0011: 개발·운영 DB를 PostgreSQL로 통일하고 초기화 SQL로 구성한다

- 상태: 채택
- 날짜: 2026-09-04
- 관련 기능: 전체 (CBAM-43 DB 환경 구성)

## 배경

ADR-0004는 개발 환경에 H2, 운영 환경에 PostgreSQL을 사용하기로 했지만 첫 운영 스키마의 적용 방식은 열어 두었다. 이후 확정된 `referdocs/ERD_v10.md`는 PostgreSQL `jsonb`와 16개 테이블 사이의 제약을 정의한다. H2와 PostgreSQL용 SQL을 따로 유지하면 발표 환경과 운영 환경의 차이를 검증하기 어렵다.

개발 기간이 짧고 팀 기능 개발이 동시에 진행 중이므로, 마이그레이션 이력 관리보다 누구나 빈 DB를 같은 상태로 빠르게 만드는 것이 우선이다. 개발자는 화면과 API를 즉시 확인할 수 있도록 기본 개발 DB에 샘플 데이터도 필요하다.

## 검토한 선택지

### A. PostgreSQL 공식 이미지의 초기화 SQL 사용 (고른 것)

- 장점 — `docker compose up`만으로 스키마와 개발용 데이터가 함께 준비된다.
- 장점 — 별도의 마이그레이션 도구와 버전 규칙을 배울 필요가 없다.
- 단점 — 초기화 SQL은 빈 데이터 볼륨에서 한 번만 실행되며, 변경 사항을 적용하려면 볼륨을 다시 만들어야 한다.
- 단점 — 누적 마이그레이션 이력을 제공하지 않으므로 장기 운영에는 적합하지 않다.

### B. Flyway 사용

- 장점 — 스키마 변경 이력과 적용 순서를 자동 관리한다.
- 고르지 않은 이유 — 짧은 개발·발표 일정에서 설정과 마이그레이션 관리 비용이 더 크다.

### C. H2 개발 DB 유지

- 장점 — 외부 DB 없이 애플리케이션을 실행할 수 있다.
- 고르지 않은 이유 — H2 JSON과 PostgreSQL `jsonb` 및 SQL 문법 차이로 실제 발표 환경의 정합성을 보장하기 어렵다.

## 결정

- `dev`와 `prod` 프로필은 PostgreSQL을 사용하고 Hibernate는 `validate`만 수행한다.
- `backend/src/main/resources/db/init_db.sql`을 모든 환경의 ERD v10 스키마 기준으로 사용한다.
- `backend/src/main/resources/db/init_demo_data.sql`은 개발·발표 환경에만 적용한다.
- 기본 `docker compose up`은 자동으로 `docker-compose.override.yml`을 합쳐 스키마와 샘플 데이터를 모두 넣는다.
- 운영은 `docker compose -f docker-compose.yml up -d`로 override를 제외해 스키마와 국가 기준정보만 넣는다.
- 테스트는 실행 격리를 위해 H2를 유지한다.
- JPA의 `EnumType.STRING`과 Hibernate 검증을 위해 ERD enum은 PostgreSQL native enum 대신 `varchar`와 `CHECK` 제약으로 표현한다.
- 현재 `Attachment` 엔티티가 체크섬을 저장하지 않으므로 `attachment.checksum_sha256`은 임시 nullable이다. 첨부 저장 기능에서 SHA-256 생성을 구현할 때 ERD처럼 not null로 강화한다.

## 결과

- 새 개발자는 PostgreSQL 컨테이너만 띄우면 발표용 데이터까지 바로 확인할 수 있다.
- 실제 이메일 대신 `example.test` 예약 도메인만 샘플 데이터에 사용한다.
- SQL 변경 뒤 재적용할 때 `docker compose down --volumes`가 필요하며, 이 명령이 DB 데이터를 삭제한다는 사실을 실행 문서에 명시한다.

## 다시 볼 조건

- 발표 이후 실제 데이터를 유지하면서 스키마를 변경해야 할 때 Flyway 도입을 다시 검토한다.
- 팀 엔티티가 모두 병합된 뒤 ERD와 실제 매핑의 차이를 제거할 때 호환 컬럼을 재검토한다.

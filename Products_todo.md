# Products 완제품 등록 API TODO

## 1. 작업 범위와 판단 기준

- 요구사항: **12번 — 완제품 등록**
- API: **POST `/api/v1/products`**
- 제외 범위: 수정·목록·상세 조회(13~15번)

자료가 다르면 다음 순서로 판단한다.

1. 현재 저장소의 `AGENTS.md`, `docs/product/REQUIREMENTS.md`, 기존 코드와 테스트, ERD·정정 문서
2. `ref/CBAM_API명세서_v10.pdf`

어느 자료로도 확정할 수 없거나 현재 코드와 ERD가 충돌하면 임의로 채우지 않고 팀에 확인한다.

## 2. 현재 진행 상태

전체 등록 API 기준으로 보면 현재는 **약 90% 진행**된 상태다. 요청 검증, 참조 데이터 일괄 조회, 세 테이블 저장, 오류 응답, HTTP Controller와 핵심 테스트까지 연결됐다. submission 도메인이 없어 등록 응답의 부품 상태를 계산하지 못하는 부분과 운영 DB 마이그레이션만 남아 있다.

### 완료 또는 거의 완료

- [x] `products/dto/ProductCreateRequest.java` 생성
  - 제품명 필수·120자 제한
  - CN코드 숫자 8자리 형식
  - 수출국 목록 필수 및 대문자 2자리 형식
  - 연간 수출량 `BigDecimal`, 0 이상, `decimal(12,2)`
  - 구성 부품 목록과 내부 DTO 검증
  - 투입량 `BigDecimal`, 0 초과, `decimal(10,3)`
- [x] `products/dto/ProductCreateResponse.java` 생성
  - 제품 기본정보와 부품명·협력사명·투입량·상태 응답 구조 작성
- [x] 필요한 패키지와 파일 골격 생성

### 엔티티 기반 완료

- [x] `PartSupplier` 독립 엔티티와 자동 생성 ID 추가
- [x] `Part`의 공급업체 관계를 `@ElementCollection`에서 `@OneToMany`로 변경
- [x] 외부 Parts API의 `supplierIds` 계약 유지
- [x] 공급 관계 제거 시 `INACTIVE`, 재추가 시 기존 행을 `ACTIVE`로 전환
- [x] Parts 공급업체 검색을 활성 `PartSupplier` 조인 방식으로 변경
- [x] `Product`, `ProductExportCountry`, `ProductPart`, `ProductStatus` 엔티티 구현
- [x] `ProductPart.part_supplier_id`가 `PartSupplier` 엔티티를 참조하도록 구현
- [x] 엔티티 매핑을 포함한 전체 백엔드 테스트 통과

### 등록 흐름 완료

- [x] `ProductsRepository.java`
- [x] `ProductsService.java`
- [x] `ProductsController.java`
- [x] Parts·Supplier 참조 데이터 일괄 조회 연결
- [x] 제품 전용 업무 예외와 오류 응답 처리
- [x] Service 단위 테스트·API 통합 테스트·Repository 영속화 테스트

## 3. Parts 구현에서 참고할 패턴

### Controller

`PartsController`처럼 Controller는 다음 일만 한다.

1. `@RestController`, `@RequestMapping`으로 경로를 연다.
2. `@Valid`로 요청 DTO 검증을 실행한다.
3. Service를 호출한다.
4. 등록 성공 시 `201 Created`를 반환한다.

제품 Controller의 목표 형태:

```java
@Tag(name = "완제품", description = "완제품 등록 API (요구사항 12번)")
@RestController
@RequestMapping("/api/v1/products")
public class ProductsController {

    private final ProductsService productsService;

    public ProductsController(ProductsService productsService) {
        this.productsService = productsService;
    }

    @PostMapping
    public ResponseEntity<ProductCreateResponse> create(
            @Valid @RequestBody ProductCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productsService.create(request));
    }
}
```

PDF에는 `X-Operator-Id`가 있지만 현재 저장소의 ADR과 기존 Controller는 인증·감사 헤더를 강제하지 않는다. 지금은 기존 코드 결정을 따른다.

### Service

`PartsService`처럼 다음 구조를 따른다.

- 클래스: `@Service`, `@Transactional(readOnly = true)`
- 등록 메서드: `@Transactional`
- 형식·범위·참조 데이터 검증을 모두 끝낸 뒤 저장
- Repository가 없음을 나타내는 경우 명세의 404 예외 발생
- 검증 메서드는 `validateCnCode`, `validateExportCountries`처럼 작게 분리

### Repository

`PartsRepository`처럼 Spring Data JPA 인터페이스로 만든다.

```java
public interface ProductsRepository extends JpaRepository<Product, Long> {
}
```

등록 기능에는 별도 검색 메서드가 당장 필요하지 않다. 제품명·CN코드 중복 금지는 요구사항 12번에 없으므로 임의로 `existsBy...` 검사를 추가하지 않는다.

### Entity

현재 저장소에는 `common/domain/BaseTimeEntity`가 생겼다. 새 Product 계열 엔티티는 직접 시간을 반복 구현하기보다 이를 상속하는 것이 현재 공통 구현에 맞다.

```java
@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTimeEntity {
    // id, name, cnCode, annualExportTon, status, 하위 목록
}
```

## 4. 해결된 설계 충돌과 남은 결정

### `part_supplier` 식별 방식

ERD는 다음 구조다.

```text
product_part.part_supplier_id → part_supplier.id
```

Parts 담당자 승인 후 **ERD 우선 방식으로 해결했다.**

- `part_supplier.id`를 독립 PK로 생성한다.
- `(part_id, supplier_id)`는 유일 제약으로 유지한다.
- 관계 상태는 `ACTIVE`/`INACTIVE`로 관리한다.
- `product_part.part_supplier_id`는 `part_supplier.id`를 FK로 참조한다.
- 요청·응답에서는 기존처럼 `partId`, `supplierId`를 사용하고 Service에서 활성 `PartSupplier`를 찾아 연결한다.

운영 DB에 기존 `part_supplier` 테이블이 있다면 배포 전에 PK·status·timestamp 컬럼 및 기존 데이터 이관 SQL이 별도로 필요하다.

### 제출 상태

등록 응답의 `parts[].status`는 해당 부품·협력사 조합의 당월 제출 상태다. 현재 submission 도메인이 없으므로 실제 값을 조회할 수 없다.

- `NOT_SUBMITTED`를 무조건 하드코딩하지 않는다.
- 등록 저장 기능과 상태 응답 기능을 분리할지 담당자와 합의한다.
- submission이 붙기 전 임시 구현을 둔다면 빈 값 또는 명시적 미구현 처리 중 무엇을 사용할지 기록한다.

### EU 회원국 조회

ERD에는 `country.is_eu_member`가 있지만 현재 Country 엔티티·Repository가 없다. EU 국가 목록을 Service에 하드코딩할지 국가 마스터를 먼저 만들지는 팀 결정이 필요하다.

## 5. 남은 구현 순서

### 1단계 — `Product.java`를 정상 엔티티로 완성 (완료)

- [x] package와 import 추가
- [x] `@Entity`, `@Table(name = "product")` 추가
- [x] `BaseTimeEntity` 상속
- [x] `@Getter`, protected 기본 생성자 추가
- [x] `id`, `name`, `cnCode`, `annualExportTon` 매핑
- [x] `ProductStatus` (`ACTIVE`, `INACTIVE`) 추가
- [x] 생성자와 하위 엔티티 추가 메서드 작성

`annualExportTon`은 `precision = 12`, `scale = 2`로 매핑한다.

### 2단계 — 하위 엔티티 완성 (완료)

`ProductExportCountry`:

- [x] `@Entity`, `@Table(name = "product_export_country")`
- [x] `BaseTimeEntity` 상속
- [x] `Product` 다대일 관계
- [x] `countryCode` 길이 2
- [x] `(product_id, country_code)` 유일 제약
- [x] protected 기본 생성자와 Product에서 호출할 생성자

`ProductPart`:

- [x] `@Entity`, `@Table(name = "product_part")`
- [x] `BaseTimeEntity` 상속
- [x] `Product` 다대일 관계
- [x] `PartSupplier` 다대일 관계와 `part_supplier_id` FK 반영
- [x] `inputQtyPerTon`을 `precision = 10`, `scale = 3`으로 매핑
- [x] `(product_id, part_supplier_id)` 유일 제약 적용
- [x] `status` 컬럼은 만들지 않음

### 3단계 — Repository 구현

- [x] `ProductsRepository`를 `JpaRepository<Product, Long>`로 변경
- [x] Product의 자식 목록에 cascade를 적용해 한 번의 `save()`로 함께 저장되는지 확인

### 4단계 — 참조 데이터 조회 구현

현재 parts 코드가 제공하는 기능을 이용하면 다음 검사는 가능하다.

1. 요청의 `(partId, supplierId)`마다 `PartSupplierRepository.findByPartIdAndSupplierIdAndStatus(..., ACTIVE)`로 활성 관계 조회
2. 관계가 없으면 부품·협력사 존재 여부를 확인해 `PART_NOT_FOUND`, `SUPPLIER_NOT_FOUND`, 공급 관계 오류를 구분
3. 조회한 `PartSupplier` 엔티티를 `Product.addPart(...)`에 전달
4. 응답용 `partName`, `supplierName` 수집

행마다 Repository를 호출하지 말고 PartsService의 `supplierNames()` 패턴처럼 ID를 모아 한 번에 조회한다.

`ProductRelatedDataProvider`를 유지할 경우 최소 계약:

```java
public interface ProductRelatedDataProvider {
    Map<Long, PartReference> findParts(Set<Long> partIds);
    Map<Long, SupplierReference> findSuppliers(Set<Long> supplierIds);
    String findCurrentMonthStatus(Long partId, Long supplierId);
}
```

등록 API만 빠르게 연결하기 위해 `ProductsService`가 `PartsRepository`와 `SupplierRepository`를 직접 읽는 방식도 현재 `PartsService`의 의존 방향과 일치한다. 다만 submission·country가 추가될 것을 고려하면 port가 경계를 더 분명하게 만든다.

### 5단계 — 오류 처리 방식 확정

현재 저장소에는 `BusinessException`, `ErrorCode`, `GlobalExceptionHandler`가 이미 있다. 따라서 parts의 도메인 전용 예외 처리기는 공통 인프라가 없을 때 만든 과도기 코드로 본다.

권장 방향:

- [ ] `ProductException` 대신 공통 `BusinessException` 사용 검토
- [ ] `INVALID_EU_COUNTRY`, `INVALID_CN_CODE`, `OUT_OF_RANGE`가 필요하면 공통 `ErrorCode`에 추가할지 팀과 합의
- [ ] 공통 예외를 사용하면 빈 `ProductApiExceptionHandling`, `ProductErrorResponse`, `ProductException`은 제거
- [ ] 공통 예외 응답의 `timestamp`, `status`, `code`, `message`, `path`, `details` 확인

`@Pattern`, `@Digits` 오류는 현재 `GlobalExceptionHandler`에서 모두 `INVALID_REQUEST`가 된다. PDF의 `INVALID_CN_CODE`, `OUT_OF_RANGE`를 정확히 내려야 한다면 DTO 검증만으로는 부족하고 Service 검증 또는 예외 매핑이 필요하다.

### 6단계 — `ProductsService.create()` 구현

권장 처리 순서:

1. CN코드와 수량 범위·정밀도를 검증한다.
2. 수출국 중복과 EU 회원국 여부를 검증한다.
3. `(partId, supplierId)` 요청 중복을 검증한다.
4. 부품 ID를 모아 한 번에 조회하고 누락 ID를 검사한다.
5. 협력사 ID를 모아 한 번에 조회하고 누락 ID를 검사한다.
6. 각 부품에 요청 협력사가 실제 공급자로 연결돼 있는지 검사한다.
7. Product와 하위 엔티티를 생성한다.
8. `productsRepository.save(product)`를 한 번 호출한다.
9. 부품명·협력사명·제출 상태를 조립해 응답한다.

모든 과정은 하나의 `@Transactional` 메서드 안에서 처리한다. 검증 중 하나라도 실패하면 세 테이블에 아무것도 남지 않아야 한다.

### 7단계 — Controller 연결

- [x] `ProductsController`에 생성자 주입 추가
- [x] `POST /api/v1/products` 추가
- [x] 요청에 `@Valid` 적용
- [x] 성공 시 `201 Created` 반환
- [ ] API 계약상 필요하면 `Location: /api/v1/products/{id}` 추가

### 8단계 — 테스트 작성

PartsServiceTest와 같은 Mockito 단위 테스트부터 작성한다.

- [x] 정상 요청이면 Product를 저장하고 응답을 반환
- [x] 없는 부품이면 `PART_NOT_FOUND`
- [ ] 없는 협력사면 `SUPPLIER_NOT_FOUND`
- [x] 부품과 협력사가 공급 관계가 아니면 등록 차단
- [ ] 잘못된 CN코드 차단
- [x] EU 회원국이 아닌 국가 차단
- [ ] 음수 연간 수출량 차단
- [ ] 0 이하 투입량 차단
- [ ] 정밀도 초과 차단
- [x] 중복 수출국 차단
- [x] 중복 부품·협력사 조합 차단
- [x] 검증 실패 시 Repository의 `save()`가 호출되지 않음

그다음 MockMvc 통합 테스트를 작성한다.

- [x] `POST /api/v1/products`가 `201` 반환
- [x] 응답 JSON의 등록 필드 확인
- [x] 필수값 누락 시 공통 에러 스키마로 `400` 반환
- [x] DB에 Product와 하위 행이 모두 저장됨

## 6. 현재 바로 해야 할 일

1. submission 도메인이 연결되면 응답 `parts[].status`를 대상 월 데이터에서 계산한다.
2. Country 마스터 도메인이 생기면 Service 내부 EU 27개 코드 집합을 Repository 조회로 교체한다.
3. 운영 DB를 사용 중이면 `part_supplier` 데이터 마이그레이션을 준비한다.

## 7. 완료 확인

- [ ] 요구사항 12번 범위만 변경했는가
- [x] `Product.java`를 포함한 전체 backend가 컴파일되는가
- [x] 수량에 `BigDecimal`을 사용했는가
- [x] part-supplier 저장 구조가 팀 결정 및 실제 parts 구현과 일치하는가
- [x] 부품·협력사 존재 여부를 행마다 조회하지 않는가
- [x] 제출 상태를 저장하거나 무조건 하드코딩하지 않았는가
- [x] 모든 저장이 한 트랜잭션으로 묶였는가
- [x] 명세에 없는 제품명·CN코드 중복 규칙을 임의로 추가하지 않았는가
- [x] 정상 동작과 주요 차단 동작을 모두 테스트했는가
- [ ] 핵심 검증을 제거하면 관련 테스트가 실제로 실패하는지 확인했는가
- [x] `./gradlew test`가 통과하는가

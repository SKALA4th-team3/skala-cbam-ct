# Naming Convention

> 코드·DB·API 명칭이 사람마다 단수/복수·케이스가 섞여서 만든 문서. 슬랙 합의(2026-09-04) 기준.
> 아래 규칙 중 하나(1번, DB Table)만 원안(GPT 초안)에서 뒤집었다 — 이미 merge된 `Supplier` 테이블과
> ERD 원본이 전부 단수형이라, 그 현실에 맞췄다. 나머지는 원안 그대로다.

---

## 1. DB Table

- **단수형** 사용, `snake_case`
- 예) `supplier`, `submission`, `mail_receipt`, `unregistered_part`

**복수형이 아니라 단수형인 이유**: ERD 원본(DBML) 16개 테이블이 전부 단수형이고, 이미 dev에 merge된
`Supplier` 엔티티도 `@Table(name = "supplier")`다. 지금 복수형으로 정하면 이미 쓰고 있는 테이블명과
어긋난다.

## 2. DB Column

- `snake_case` 사용
- 예) `user_id`, `created_at`, `updated_at`

## 3. API URL

- **복수형** 사용, 소문자, 단어 구분은 **kebab-case**
- 예) `/suppliers`, `/mail-receipts`, `/suppliers/{supplierId}`
- 컬렉션이 아니라 화면 하나를 가리키는 리소스(예: 대시보드)는 예외로 단수 유지 — `/dashboard`

## 4. Java Class (Entity 포함)

- **단수형** 사용, `PascalCase`
- 예) `Supplier`, `Submission`, `MailReceipt`

## 5. Java Variable / Method

- `camelCase` 사용
- 예) `userId`, `orderItem`, `getUser()`, `createUser()`

## 6. DTO

- **대상 + 역할** 형태
- 예) `SupplierCreateRequest`, `SubmissionDetailResponse`, `MailReceiptMatchResponse`

## 7. Repository / Service / Controller

- **Entity 이름 + 역할**
- 예) `SupplierRepository`, `SubmissionService`, `MailController`

---

## 지금까지 나온 코드와 대조

| 대상 | 이미 있는 예 | 이 문서와 일치 |
| --- | --- | --- |
| DB Table | `supplier`, `submission`, `mail_receipt` | ✅ (1번 규칙을 이 예에 맞춰 정함) |
| DB Column | `business_registration_number`, `part_supplier_id` | ✅ |
| API URL | `/api/v1/suppliers`, `/api/v1/mail-receipts` | ✅ |
| Java Class | `Supplier`, `Submission`, `MailReceipt` | ✅ |
| DTO | `SupplierCreateRequest`, `SubmissionRejectResponse` | ✅ |
| Repository/Service/Controller | `SupplierRepository`, `MailService`, `SubmissionController` | ✅ |

새로 짤 때 이미 있는 도메인(`supplier`)의 이름을 그대로 참고하면 대부분 맞는다.

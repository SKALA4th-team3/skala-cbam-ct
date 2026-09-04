-- 로컬 통합 테스트용 가상 데이터. 실제 개인정보가 아니다.
-- `--spring.profiles.active=dev,mock`으로 실행할 때만 적재된다.

MERGE INTO country (code, name, is_eu_member) KEY(code) VALUES
('KR', '대한민국', FALSE), ('DE', '독일', TRUE), ('FR', '프랑스', TRUE), ('NL', '네덜란드', TRUE);

MERGE INTO supplier (id, business_registration_number, name, country_code, contact_name,
    contact_email, contact_phone, status, created_at, updated_at) KEY(id) VALUES
(10001, 'MOCK-101-81-00001', '샘플 철강', 'KR', '김테스트', 'supplier1@example.test',
 '02-0000-0001', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(10002, 'MOCK-101-81-00002', '샘플 알루미늄', 'KR', '이테스트', 'supplier2@example.test',
 '02-0000-0002', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

MERGE INTO part (id, part_code, name, cn_code, unit, benchmark_factor, benchmark_factor_year,
    status, created_at, updated_at) KEY(id) VALUES
(10001, 'MOCK-PART-STEEL', '샘플 철강 프레임', '73269098', 'KG', 1.8500, 2026, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(10002, 'MOCK-PART-AL', '샘플 알루미늄 패널', '76169990', 'KG', 2.4000, 2026, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

MERGE INTO part_supplier (id, part_id, supplier_id, status, created_at, updated_at) KEY(id) VALUES
(10001, 10001, 10001, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(10002, 10002, 10002, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

MERGE INTO product (id, name, cn_code, annual_export_ton, status, created_at, updated_at) KEY(id) VALUES
(10001, '샘플 전기차 차체', '87082990', 12500.00, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

MERGE INTO product_export_country (id, product_id, country_code, created_at, updated_at) KEY(id) VALUES
(10001, 10001, 'DE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(10002, 10001, 'FR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

MERGE INTO product_part (id, product_id, part_supplier_id, input_qty_per_ton, created_at, updated_at) KEY(id) VALUES
(10001, 10001, 10001, 620.000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(10002, 10001, 10002, 180.000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

MERGE INTO mail_receipt (id, supplier_id, message_id, sender_email, subject, body, status,
    received_at, created_at) KEY(id) VALUES
(10001, 10001, '<mock-2026-08-001@example.test>', 'supplier1@example.test',
 '2026년 8월 탄소배출 자료', '테스트용 제출 메일입니다.', 'ANALYZED',
 TIMESTAMP WITH TIME ZONE '2026-09-01 09:00:00+09:00', CURRENT_TIMESTAMP);

MERGE INTO attachment (id, mail_receipt_id, original_filename, storage_uri, mime_type, size_bytes,
    checksum_sha256, process_status, extracted_text_uri, created_at, updated_at) KEY(id) VALUES
(10001, 10001, 'mock-emission-2026-08.xlsx', 'mock://attachments/mock-emission-2026-08.xlsx',
 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', 2048,
 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 'EXTRACTED',
 'mock://extracted/mock-emission-2026-08.txt', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

MERGE INTO submission (id, mail_receipt_id, supplier_id, part_supplier_id, reporting_month,
    document_type, document_month, production_quantity_ton, production_country_code,
    direct_emission_tco2e, indirect_emission_tco2e, default_value_ratio,
    applied_factor_year, applied_benchmark_factor, factor_frozen_at, status, judgement,
    eligibility_status, severity, submitted_at, confirmed_by, confirmed_at, created_at, updated_at) KEY(id) VALUES
(10001, 10001, 10001, 10001, DATE '2026-08-01', 'EMISSION_STATEMENT', DATE '2026-08-01',
 850.000, 'KR', 1200.000, 240.000, 0.0500, 2026, 1.8500, CURRENT_TIMESTAMP,
 'CONFIRMED', 'QUALIFIED', 'USABLE', 'LOW', TIMESTAMP WITH TIME ZONE '2026-09-01 09:00:00+09:00',
 'mock-reviewer', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

MERGE INTO extraction_field (id, submission_id, source_attachment_id, field_code, sequence_number,
    value_type, normalized_decimal, unit, raw_value, emission_scope, source_locator,
    created_at, updated_at) KEY(id) VALUES
(10001, 10001, 10001, 'PRODUCTION', 1, 'DECIMAL', 850.00000000, 'TON', '850 ton', NULL,
 'Sheet1!B3', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(10002, 10001, 10001, 'FUEL_LNG', 1, 'DECIMAL', 1200.00000000, 'tCO2e', '1,200 tCO2e',
 'DIRECT', 'Sheet1!B8', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

MERGE INTO unregistered_part (id, submission_id, raw_part_name, mapped_part_supplier_id, status,
    resolved_by, resolved_at, created_at, updated_at) KEY(id) VALUES
(10001, 10001, '과거 미등록 샘플 철강 프레임', 10001, 'RESOLVED', 'mock-reviewer',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

MERGE INTO feedback (id, supplier_id, part_supplier_id, submission_id, reporting_month, type,
    status, recipient_email, confirmed_by, locked_at, created_by, created_at, updated_at) KEY(id) VALUES
(10001, 10001, 10001, 10001, DATE '2026-08-01', 'FEEDBACK', 'READY_TO_SEND',
 'supplier1@example.test', 'mock-reviewer', CURRENT_TIMESTAMP, 'mock-reviewer', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

MERGE INTO feedback_draft (id, feedback_id, version_number, source_type, style, subject, body,
    fallback_applied, created_at, updated_at) KEY(id) VALUES
(10001, 10001, 1, 'HUMAN_EDIT', 'FORMAL', '[테스트] 2026년 8월 제출 확인',
 '테스트 데이터 확인용 피드백 본문입니다.', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

UPDATE feedback SET confirmed_draft_id = 10001 WHERE id = 10001;

MERGE INTO task (id, mail_receipt_id, submission_id, feedback_id, feedback_draft_id, type, status,
    delivery_status, resource_type, resource_ids, progress_total, progress_done, progress_failed,
    fallback_applied, unregistered_part_count, attempt_number, recipient_email,
    external_message_id, requested_by, sent_at, started_at, completed_at, created_at, updated_at) KEY(id) VALUES
('tsk-mock-10001', 10001, 10001, 10001, 10001, 'SEND_FEEDBACK', 'COMPLETED', 'SENT',
 'FEEDBACK', JSON '[10001]', 1, 1, 0, FALSE, 0, 1, 'supplier1@example.test',
 '<mock-sent-10001@example.test>', 'mock-reviewer', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

MERGE INTO alert (id, part_supplier_id, submission_id, reporting_month, rule_id, check_id, outcome,
    severity, observed_value, reference_value, message, status, validated_at, resolved_at,
    created_at, updated_at) KEY(id) VALUES
(10001, 10001, 10001, DATE '2026-08-01', 'R6', 'AVG_DEVIATION', 'PASS', 'LOW',
 '1.69', '1.85', '기준 배출원단위 허용 범위입니다.', 'RESOLVED', CURRENT_TIMESTAMP,
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ─────────────────────────────────────────────────────────────
--  시연용 추가 데이터
--
--  위 기본 한 벌만으로는 핵심 줄(29번 검토 → 31번 확정 → 41번 집계 → 42번 피드백)을
--  눌러 볼 수 없다. 제출이 한 건뿐이고 그마저 이미 확정 상태라 확정·반려 버튼이 전부 막힌다.
--  그래서 두 달치를 만든다.
--
--    2026-08  끝난 달   — 두 부품 모두 확정 → 완제품 내재배출량이 실제로 계산된다
--    2026-09  진행 중   — 적격 1건(확정 대기) · 부적격 1건(이상치) → 확정·반려·피드백 시연
--                       + 발신자를 못 찾은 미확인 접수 1건 → 수동 매칭 시연
-- ─────────────────────────────────────────────────────────────

MERGE INTO mail_receipt (id, supplier_id, message_id, sender_email, subject, body, status,
    received_at, created_at) KEY(id) VALUES
(10002, 10002, '<mock-2026-08-002@example.test>', 'supplier2@example.test',
 '2026년 8월 탄소배출 자료', '알루미늄 패널 8월 실적입니다.', 'ANALYZED',
 TIMESTAMP WITH TIME ZONE '2026-09-01 10:20:00+09:00', CURRENT_TIMESTAMP),
(10003, 10001, '<mock-2026-09-001@example.test>', 'supplier1@example.test',
 '2026년 9월 탄소배출 자료', '9월 실적 보내드립니다.', 'ANALYZED',
 TIMESTAMP WITH TIME ZONE '2026-09-03 09:10:00+09:00', CURRENT_TIMESTAMP),
(10004, 10002, '<mock-2026-09-002@example.test>', 'supplier2@example.test',
 '9월 배출량 회신', '9월 자료 첨부합니다.', 'ANALYZED',
 TIMESTAMP WITH TIME ZONE '2026-09-03 14:40:00+09:00', CURRENT_TIMESTAMP),
-- 19번: 담당자 이메일과 일치하는 업체가 없어 「미확인」으로 둔 건. 21번에서 직접 지정한다.
(10005, NULL, '<mock-2026-09-003@example.test>', 'unknown-sender@example.test',
 '탄소 배출 자료 전달드립니다', '어느 업체인지 알 수 없는 발신자입니다.', 'UNMATCHED',
 TIMESTAMP WITH TIME ZONE '2026-09-04 08:05:00+09:00', CURRENT_TIMESTAMP);

MERGE INTO attachment (id, mail_receipt_id, original_filename, storage_uri, mime_type, size_bytes,
    checksum_sha256, process_status, extracted_text_uri, created_at, updated_at) KEY(id) VALUES
(10002, 10002, 'mock-al-2026-08.xlsx', 'mock://attachments/mock-al-2026-08.xlsx',
 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', 2048,
 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', 'EXTRACTED',
 'mock://extracted/mock-al-2026-08.txt', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(10003, 10003, 'mock-steel-2026-09.xlsx', 'mock://attachments/mock-steel-2026-09.xlsx',
 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', 2048,
 'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc', 'EXTRACTED',
 'mock://extracted/mock-steel-2026-09.txt', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(10004, 10004, 'mock-al-2026-09.xlsx', 'mock://attachments/mock-al-2026-09.xlsx',
 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', 2048,
 'dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd', 'EXTRACTED',
 'mock://extracted/mock-al-2026-09.txt', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(10005, 10005, 'mock-unknown-2026-09.pdf', 'mock://attachments/mock-unknown-2026-09.pdf',
 'application/pdf', 4096,
 'eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee', 'PENDING',
 NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 2026-08 알루미늄 확정분. 철강(10001)과 함께 완제품 두 부품이 모두 확정돼
-- 완제품 상세의 내재배출량이 null 이 아니라 실제 값으로 나온다 (15번).
--   (900 + 180) / 500 = 2.1600  ×  투입량 180  =  388.80
--   철강 1050.342 + 알루미늄 388.80 = 1439.142   (평균값 1579.00 대비 -8.9%)
MERGE INTO submission (id, mail_receipt_id, supplier_id, part_supplier_id, reporting_month,
    document_type, document_month, production_quantity_ton, production_country_code,
    direct_emission_tco2e, indirect_emission_tco2e, default_value_ratio,
    applied_factor_year, applied_benchmark_factor, factor_frozen_at, status, judgement,
    eligibility_status, severity, submitted_at, confirmed_by, confirmed_at,
    rejected_by, rejected_at, rejection_reason_code, rejection_reason,
    created_at, updated_at) KEY(id) VALUES
(10002, 10002, 10002, 10002, DATE '2026-08-01', 'EMISSION_STATEMENT', DATE '2026-08-01',
 500.000, 'KR', 900.000, 180.000, 0.0000, 2026, 2.4000, CURRENT_TIMESTAMP,
 'CONFIRMED', 'QUALIFIED', 'USABLE', 'LOW', TIMESTAMP WITH TIME ZONE '2026-09-01 10:30:00+09:00',
 'mock-reviewer', CURRENT_TIMESTAMP, NULL, NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- 31번 확정 시연용. 적격 · 미등록 부품 없음 · 부품에 배출계수 연도(2026)가 있어 확정이 통과한다.
--   (1300 + 260) / 900 = 1.7333
(10003, 10003, 10001, 10001, DATE '2026-09-01', 'EMISSION_STATEMENT', DATE '2026-09-01',
 900.000, 'KR', 1300.000, 260.000, 0.0000, NULL, NULL, NULL,
 'REVIEW_PENDING', 'QUALIFIED', 'USABLE', 'LOW', TIMESTAMP WITH TIME ZONE '2026-09-03 09:20:00+09:00',
 NULL, NULL, NULL, NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- 32번 반려 · 42번 피드백 초안 시연용. 평균값 2.4000 대비 배출 원단위가 크게 높다.
--   (1500 + 300) / 400 = 4.5000  →  평균 대비 +87.5% (34번 이상치 임계 ±30% 초과, ADR-0001)
(10004, 10004, 10002, 10002, DATE '2026-09-01', 'EMISSION_STATEMENT', DATE '2026-09-01',
 400.000, 'KR', 1500.000, 300.000, 0.1500, NULL, NULL, NULL,
 'REVIEW_PENDING', 'UNQUALIFIED', 'NOT_USABLE', 'HIGH', TIMESTAMP WITH TIME ZONE '2026-09-03 14:50:00+09:00',
 NULL, NULL, NULL, NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

MERGE INTO extraction_field (id, submission_id, source_attachment_id, field_code, sequence_number,
    value_type, normalized_decimal, unit, raw_value, emission_scope, source_locator,
    created_at, updated_at) KEY(id) VALUES
(10003, 10002, 10002, 'PRODUCTION', 1, 'DECIMAL', 500.00000000, 'TON', '500 ton', NULL,
 'Sheet1!B3', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(10004, 10003, 10003, 'PRODUCTION', 1, 'DECIMAL', 900.00000000, 'TON', '900 ton', NULL,
 'Sheet1!B3', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(10005, 10004, 10004, 'PRODUCTION', 1, 'DECIMAL', 400.00000000, 'TON', '400 ton', NULL,
 'Sheet1!B3', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
-- 24번: 단위를 몰라 옮기지 못한 값은 비우고 사유를 남긴다
(10006, 10004, 10004, 'FUEL_LNG', 1, 'TEXT', NULL, NULL, '가스 사용량 다수', 'DIRECT',
 'Sheet1!B9', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 40번 심각도별 건수 · 대시보드 경보 목록이 비어 있지 않도록 한다
MERGE INTO alert (id, part_supplier_id, submission_id, reporting_month, rule_id, check_id, outcome,
    severity, observed_value, reference_value, message, status, validated_at, resolved_at,
    created_at, updated_at) KEY(id) VALUES
(10002, 10002, 10004, DATE '2026-09-01', 'R6', 'AVG_DEVIATION', 'FAIL', 'HIGH',
 '4.50', '2.40', '배출 원단위가 평균값보다 87.5% 높습니다. 확인이 필요합니다.', 'OPEN',
 CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(10003, 10002, 10004, DATE '2026-09-01', 'R5', 'UNIT_MISSING', 'FAIL', 'MEDIUM',
 '가스 사용량 다수', NULL, '단위를 알 수 없어 표준 단위로 옮기지 못한 값이 있습니다.', 'OPEN',
 CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

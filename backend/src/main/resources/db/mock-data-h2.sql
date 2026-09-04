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
 'DELIVERY', JSON '[10001]', 1, 1, 0, FALSE, 0, 1, 'supplier1@example.test',
 '<mock-sent-10001@example.test>', 'mock-reviewer', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

MERGE INTO alert (id, part_supplier_id, submission_id, reporting_month, rule_id, check_id, outcome,
    severity, observed_value, reference_value, message, status, validated_at, resolved_at,
    created_at, updated_at) KEY(id) VALUES
(10001, 10001, 10001, DATE '2026-08-01', 'R6', 'AVG_DEVIATION', 'PASS', 'LOW',
 '1.69', '1.85', '기준 배출원단위 허용 범위입니다.', 'RESOLVED', CURRENT_TIMESTAMP,
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

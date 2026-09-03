-- 개발·발표용 데이터. 실제 개인정보 대신 RFC 2606 예약 도메인 example.test를 사용한다.
-- 날짜는 실행 시점의 현재 월을 기준으로 만들어 발표 시점이 바뀌어도 화면 의미가 유지된다.

INSERT INTO supplier (
    id, business_registration_number, name, country_code, contact_name, contact_email,
    contact_phone, status, status_reason, inactive_at
) VALUES
    (1001, 'DEMO-BIZ-001', '한빛스틸', 'KR', '김담당', 'supplier01@example.test', '02-0000-1001', 'ACTIVE', NULL, NULL),
    (1002, 'DEMO-BIZ-002', '푸른알루미늄', 'KR', '이담당', 'supplier02@example.test', '02-0000-1002', 'ACTIVE', NULL, NULL),
    (1003, 'DEMO-BIZ-003', '글로벌소재', 'CN', '왕담당', 'supplier03@example.test', NULL, 'ACTIVE', NULL, NULL),
    (1004, 'DEMO-BIZ-004', '옛날금속', 'KR', '박담당', 'supplier04@example.test', NULL, 'INACTIVE', '거래 종료', CURRENT_TIMESTAMP - interval '60 days')
ON CONFLICT DO NOTHING;

INSERT INTO part (id, part_code, name, cn_code, unit, benchmark_factor, benchmark_factor_year, status) VALUES
    (2001, 'PART-STEEL-PLATE', '열연강판', '72081000', 'TON', 2.1000, EXTRACT(YEAR FROM CURRENT_DATE)::smallint, 'ACTIVE'),
    (2002, 'PART-AL-FRAME', '알루미늄 프레임', '76042990', 'TON', 8.3000, EXTRACT(YEAR FROM CURRENT_DATE)::smallint, 'ACTIVE'),
    (2003, 'PART-BOLT', '체결 볼트', '73181590', 'KG', 3.0000, EXTRACT(YEAR FROM CURRENT_DATE)::smallint, 'ACTIVE')
ON CONFLICT DO NOTHING;

INSERT INTO part_supplier (id, part_id, supplier_id, status) VALUES
    (3001, 2001, 1001, 'ACTIVE'),
    (3002, 2002, 1002, 'ACTIVE'),
    (3003, 2003, 1003, 'ACTIVE')
ON CONFLICT DO NOTHING;

INSERT INTO product (id, name, cn_code, annual_export_ton, status) VALUES
    (4001, 'CBAM 데모 완제품 A', '73269098', 12500.00, 'ACTIVE'),
    (4002, 'CBAM 데모 완제품 B', '76169990', 7200.00, 'ACTIVE')
ON CONFLICT DO NOTHING;

INSERT INTO product_export_country (id, product_id, country_code) VALUES
    (4101, 4001, 'DE'),
    (4102, 4001, 'PL'),
    (4103, 4002, 'FR')
ON CONFLICT DO NOTHING;

INSERT INTO product_part (id, product_id, part_supplier_id, input_qty_per_ton) VALUES
    (4201, 4001, 3001, 0.720),
    (4202, 4001, 3003, 0.015),
    (4203, 4002, 3002, 0.580)
ON CONFLICT DO NOTHING;

INSERT INTO mail_receipt (
    id, supplier_id, message_id, sender_email, subject, body, status, failure_reason,
    linked_by, linked_at, latest_analysis_task_id, received_at
) VALUES
    (5001, 1001, '<demo-5001@example.test>', 'supplier01@example.test', '이번 달 배출자료 제출', '열연강판 배출자료를 제출합니다.', 'ANALYZED', NULL, NULL, NULL, 'tsk-demo-analysis-1', CURRENT_TIMESTAMP - interval '2 days'),
    (5002, NULL, '<demo-5002@example.test>', 'unknown01@example.test', '업체 확인이 필요한 배출자료', '등록되지 않은 발신자입니다.', 'UNMATCHED', NULL, NULL, NULL, NULL, CURRENT_TIMESTAMP - interval '1 day'),
    (5003, 1002, '<demo-5003@example.test>', 'supplier02@example.test', '알루미늄 배출자료', '단위 확인이 필요한 자료입니다.', 'ANALYZED', NULL, NULL, NULL, 'tsk-demo-analysis-2', CURRENT_TIMESTAMP - interval '8 hours'),
    (5004, 1003, '<demo-5004@example.test>', 'supplier03@example.test', '암호화 첨부파일', '첨부파일 암호를 확인해 주세요.', 'REJECTED', 'ENCRYPTED_FILE', NULL, NULL, NULL, CURRENT_TIMESTAMP - interval '5 hours'),
    (5005, 1001, '<demo-5005@example.test>', 'supplier01@example.test', '분석 재요청 자료', 'AI 분석 중 오류가 발생한 예시입니다.', 'ANALYSIS_FAILED', 'AI_TIMEOUT', NULL, NULL, 'tsk-demo-analysis-failed', CURRENT_TIMESTAMP - interval '3 hours')
ON CONFLICT DO NOTHING;

INSERT INTO attachment (
    id, mail_receipt_id, original_filename, storage_uri, mime_type, size_bytes,
    checksum_sha256, process_status, extracted_text_uri, failure_reason
) VALUES
    (6001, 5001, 'emission-demo-01.xlsx', 'demo://attachments/emission-demo-01.xlsx', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', 24576, repeat('a', 64), 'EXTRACTED', 'demo://extracted/emission-demo-01.json', NULL),
    (6002, 5003, 'aluminium-demo.pdf', 'demo://attachments/aluminium-demo.pdf', 'application/pdf', 32768, repeat('b', 64), 'EXTRACTED', 'demo://extracted/aluminium-demo.json', NULL),
    (6003, 5004, 'encrypted-demo.xlsx', 'demo://attachments/encrypted-demo.xlsx', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', 18432, repeat('c', 64), 'FAILED', NULL, 'ENCRYPTED_FILE'),
    (6004, 5005, 'retry-demo.csv', 'demo://attachments/retry-demo.csv', 'text/csv', 4096, repeat('d', 64), 'PENDING', NULL, NULL)
ON CONFLICT DO NOTHING;

INSERT INTO submission (
    id, mail_receipt_id, supplier_id, part_supplier_id, reporting_month, document_type,
    document_month, production_quantity_ton, production_country_code,
    direct_emission_tco2e, indirect_emission_tco2e, default_value_ratio,
    applied_factor_year, applied_benchmark_factor, factor_frozen_at,
    status, judgement, eligibility_status, severity, submitted_at,
    confirmed_by, confirmed_at, rejected_by, rejected_at, rejection_reason_code, rejection_reason
) VALUES
    (7001, 5001, 1001, 3001, date_trunc('month', CURRENT_DATE)::date, 'EMISSION_STATEMENT', date_trunc('month', CURRENT_DATE)::date,
     1000.000, 'KR', 1500.000, 400.000, 0.0000, EXTRACT(YEAR FROM CURRENT_DATE)::smallint, 2.1000, CURRENT_TIMESTAMP - interval '1 day',
     'CONFIRMED', 'QUALIFIED', 'USABLE', 'LOW', CURRENT_TIMESTAMP - interval '2 days', 'demo-reviewer', CURRENT_TIMESTAMP - interval '1 day', NULL, NULL, NULL, NULL),
    (7002, 5003, 1002, 3002, date_trunc('month', CURRENT_DATE)::date, 'EMISSION_STATEMENT', date_trunc('month', CURRENT_DATE)::date,
     520.000, 'KR', 5100.000, 700.000, 0.1200, EXTRACT(YEAR FROM CURRENT_DATE)::smallint, NULL, NULL,
     'REVIEW_PENDING', 'UNQUALIFIED', 'NOT_USABLE', 'HIGH', CURRENT_TIMESTAMP - interval '8 hours', NULL, NULL, NULL, NULL, NULL, NULL),
    (7003, 5003, 1002, NULL, date_trunc('month', CURRENT_DATE)::date, 'EMISSION_STATEMENT', date_trunc('month', CURRENT_DATE)::date,
     120.000, 'KR', NULL, NULL, 0.0000, NULL, NULL, NULL,
     'REVIEW_PENDING', 'UNQUALIFIED', 'NOT_USABLE', 'MEDIUM', CURRENT_TIMESTAMP - interval '8 hours', NULL, NULL, NULL, NULL, NULL, NULL)
ON CONFLICT DO NOTHING;

INSERT INTO extraction_field (
    id, submission_id, source_attachment_id, field_code, sequence_number, value_type,
    normalized_text, normalized_decimal, normalized_date, normalized_country_code,
    unit, raw_value, emission_scope, source_locator, conversion_failure_reason
) VALUES
    (8001, 7001, 6001, 'PART_NAME', 1, 'TEXT', '열연강판', NULL, NULL, NULL, NULL, '열연강판', NULL, 'Sheet1!A2', NULL),
    (8002, 7001, 6001, 'PRODUCTION', 1, 'DECIMAL', NULL, 1000.00000000, NULL, NULL, 'TON', '1,000 ton', NULL, 'Sheet1!B2', NULL),
    (8003, 7001, 6001, 'DIRECT_EMISSION', 1, 'DECIMAL', NULL, 1500.00000000, NULL, NULL, 'tCO2e', '1,500 tCO2e', 'DIRECT', 'Sheet1!C2', NULL),
    (8004, 7001, 6001, 'DOCUMENT_MONTH', 1, 'DATE', NULL, NULL, date_trunc('month', CURRENT_DATE)::date, NULL, NULL, to_char(CURRENT_DATE, 'YYYY-MM'), NULL, 'Sheet1!F2', NULL),
    (8005, 7002, 6002, 'ELECTRICITY', 1, 'DECIMAL', NULL, NULL, NULL, NULL, NULL, '약 700 전력', 'INDIRECT', 'PDF page 1', 'UNIT_NOT_RECOGNIZED'),
    (8006, 7003, 6002, 'PART_NAME', 1, 'TEXT', '신규 합금 브래킷', NULL, NULL, NULL, NULL, '신규 합금 브래킷', NULL, 'PDF page 2', NULL)
ON CONFLICT DO NOTHING;

INSERT INTO unregistered_part (id, submission_id, raw_part_name, status) VALUES
    (9001, 7003, '신규 합금 브래킷', 'OPEN')
ON CONFLICT DO NOTHING;

INSERT INTO alert (
    id, part_supplier_id, unregistered_part_id, submission_id, reporting_month,
    rule_id, check_id, outcome, severity, observed_value, reference_value, message,
    status, validated_at
) VALUES
    (10001, 3002, NULL, 7002, date_trunc('month', CURRENT_DATE)::date, 'R4', 'AVG_DEVIATION', 'FAIL', 'HIGH', '11.1538', '8.3000 ±30%', '동일 품목 평균 대비 배출 원단위가 허용 범위를 벗어났습니다.', 'OPEN', CURRENT_TIMESTAMP - interval '7 hours'),
    (10002, NULL, 9001, 7003, date_trunc('month', CURRENT_DATE)::date, 'R2', 'UNREGISTERED_PART', 'FAIL', 'MEDIUM', '신규 합금 브래킷', NULL, '등록된 부품과 일치하지 않습니다.', 'OPEN', CURRENT_TIMESTAMP - interval '7 hours'),
    (10003, 3003, NULL, NULL, date_trunc('month', CURRENT_DATE)::date, 'R1', 'NOT_SUBMITTED', 'FAIL', 'HIGH', NULL, to_char(date_trunc('month', CURRENT_DATE) + interval '1 month - 1 day', 'YYYY-MM-DD'), '현재 보고 월의 배출자료가 제출되지 않았습니다.', 'OPEN', CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

INSERT INTO feedback (
    id, supplier_id, part_supplier_id, submission_id, reporting_month, type, status, created_by
) VALUES
    (11001, 1002, 3002, 7002, date_trunc('month', CURRENT_DATE)::date, 'FEEDBACK', 'DRAFT', 'demo-reviewer'),
    (11002, 1003, 3003, NULL, date_trunc('month', CURRENT_DATE)::date, 'REMINDER', 'DRAFT', 'demo-reviewer')
ON CONFLICT DO NOTHING;

INSERT INTO feedback_draft (
    id, feedback_id, version_number, source_type, style, subject, body, fallback_applied, fallback_template_id
) VALUES
    (12001, 11001, 1, 'AI', 'FORMAL', '[확인 요청] 배출자료 이상치 확인', '제출하신 배출자료의 원단위가 평균 범위를 벗어나 확인을 요청드립니다.', false, NULL),
    (12002, 11002, 1, 'FALLBACK_TEMPLATE', 'CONCISE', '[제출 안내] 이번 달 배출자료', '이번 달 배출자료가 아직 접수되지 않았습니다.', true, 'REMINDER_DEFAULT_V1')
ON CONFLICT DO NOTHING;

INSERT INTO task (
    id, mail_receipt_id, submission_id, type, status, resource_type, resource_ids,
    progress_total, progress_done, progress_failed, unregistered_part_count,
    error_code, error_message, requested_by, started_at, completed_at
) VALUES
    ('tsk-demo-analysis-1', 5001, 7001, 'ANALYZE_MAIL_RECEIPT', 'COMPLETED', 'submission', '[7001]'::jsonb, 1, 1, 0, 0, NULL, NULL, 'system', CURRENT_TIMESTAMP - interval '2 days', CURRENT_TIMESTAMP - interval '2 days' + interval '2 minutes'),
    ('tsk-demo-analysis-2', 5003, 7002, 'ANALYZE_MAIL_RECEIPT', 'COMPLETED', 'submission', '[7002, 7003]'::jsonb, 2, 2, 0, 1, NULL, NULL, 'system', CURRENT_TIMESTAMP - interval '8 hours', CURRENT_TIMESTAMP - interval '7 hours'),
    ('tsk-demo-analysis-failed', 5005, NULL, 'ANALYZE_MAIL_RECEIPT', 'FAILED', NULL, NULL, 1, 0, 1, 0, 'AI_TIMEOUT', 'AI 분석 응답 시간이 초과되었습니다.', 'system', CURRENT_TIMESTAMP - interval '3 hours', CURRENT_TIMESTAMP - interval '2 hours 55 minutes')
ON CONFLICT DO NOTHING;

SELECT setval(pg_get_serial_sequence('supplier', 'id'), GREATEST((SELECT max(id) FROM supplier), 1), true);
SELECT setval(pg_get_serial_sequence('part', 'id'), GREATEST((SELECT max(id) FROM part), 1), true);
SELECT setval(pg_get_serial_sequence('part_supplier', 'id'), GREATEST((SELECT max(id) FROM part_supplier), 1), true);
SELECT setval(pg_get_serial_sequence('product', 'id'), GREATEST((SELECT max(id) FROM product), 1), true);
SELECT setval(pg_get_serial_sequence('product_export_country', 'id'), GREATEST((SELECT max(id) FROM product_export_country), 1), true);
SELECT setval(pg_get_serial_sequence('product_part', 'id'), GREATEST((SELECT max(id) FROM product_part), 1), true);
SELECT setval(pg_get_serial_sequence('mail_receipt', 'id'), GREATEST((SELECT max(id) FROM mail_receipt), 1), true);
SELECT setval(pg_get_serial_sequence('attachment', 'id'), GREATEST((SELECT max(id) FROM attachment), 1), true);
SELECT setval(pg_get_serial_sequence('submission', 'id'), GREATEST((SELECT max(id) FROM submission), 1), true);
SELECT setval(pg_get_serial_sequence('extraction_field', 'id'), GREATEST((SELECT max(id) FROM extraction_field), 1), true);
SELECT setval(pg_get_serial_sequence('unregistered_part', 'id'), GREATEST((SELECT max(id) FROM unregistered_part), 1), true);
SELECT setval(pg_get_serial_sequence('alert', 'id'), GREATEST((SELECT max(id) FROM alert), 1), true);
SELECT setval(pg_get_serial_sequence('feedback', 'id'), GREATEST((SELECT max(id) FROM feedback), 1), true);
SELECT setval(pg_get_serial_sequence('feedback_draft', 'id'), GREATEST((SELECT max(id) FROM feedback_draft), 1), true);

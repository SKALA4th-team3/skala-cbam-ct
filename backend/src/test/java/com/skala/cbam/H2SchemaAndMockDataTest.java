package com.skala.cbam;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/** ERD 기반 H2 스키마 생성, JPA 매핑 검증, mock 데이터 적재를 한 번에 확인한다. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:cbam-schema-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/schema-h2.sql,classpath:db/schema-test-support.sql",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles({"test", "mock"})
class H2SchemaAndMockDataTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void erd_16개_테이블과_mock_데이터를_사용할_수_있다() {
        Integer tableCount = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM INFORMATION_SCHEMA.TABLES
                        WHERE TABLE_SCHEMA = 'PUBLIC'
                          AND TABLE_NAME IN (
                            'COUNTRY', 'SUPPLIER', 'PART', 'PART_SUPPLIER', 'PRODUCT',
                            'PRODUCT_EXPORT_COUNTRY', 'PRODUCT_PART', 'MAIL_RECEIPT', 'ATTACHMENT',
                            'SUBMISSION', 'EXTRACTION_FIELD', 'UNREGISTERED_PART', 'TASK', 'ALERT',
                            'FEEDBACK', 'FEEDBACK_DRAFT'
                          )
                        """)
                .query(Integer.class)
                .single();

        assertThat(tableCount).isEqualTo(16);
        assertThat(count("supplier")).isPositive();
        assertThat(count("part")).isPositive();
        assertThat(count("product")).isPositive();
        assertThat(count("feedback_draft")).isPositive();

        // 개수를 못박지 않는다 — 시연 시나리오가 늘면 그때마다 깨진다.
        // 대신 시연이 성립하는 조건을 단언한다: 눌러 볼 것이 실제로 있어야 한다.
        assertThat(countWhere("submission", "status = 'CONFIRMED'"))
                .as("완제품 내재배출량(15번)이 계산되려면 확정 건이 있어야 한다")
                .isPositive();
        assertThat(countWhere("submission", "status = 'REVIEW_PENDING' AND judgement = 'QUALIFIED'"))
                .as("31번 확정을 눌러 보려면 적격 검토 대기 건이 있어야 한다")
                .isPositive();
        assertThat(countWhere("submission", "status = 'REVIEW_PENDING' AND judgement = 'UNQUALIFIED'"))
                .as("32번 반려와 42번 피드백 초안을 눌러 보려면 부적격 건이 있어야 한다")
                .isPositive();
        assertThat(countWhere("part", "benchmark_factor_year IS NOT NULL"))
                .as("31번 확정은 부품의 배출계수 연도를 스냅샷으로 찍는다 — 없으면 확정이 막힌다")
                .isPositive();
        assertThat(countWhere("mail_receipt", "status = 'UNMATCHED'"))
                .as("21번 수동 매칭을 눌러 보려면 미확인 접수 건이 있어야 한다")
                .isPositive();
    }

    private Integer count(String table) {
        return jdbcClient.sql("SELECT COUNT(*) FROM " + table)
                .query(Integer.class)
                .single();
    }

    private Integer countWhere(String table, String condition) {
        return jdbcClient.sql("SELECT COUNT(*) FROM " + table + " WHERE " + condition)
                .query(Integer.class)
                .single();
    }
}

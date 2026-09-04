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
        assertThat(count("supplier")).isEqualTo(2);
        assertThat(count("part")).isEqualTo(2);
        assertThat(count("product")).isEqualTo(1);
        assertThat(count("submission")).isEqualTo(1);
        assertThat(count("feedback_draft")).isEqualTo(1);
    }

    private Integer count(String table) {
        return jdbcClient.sql("SELECT COUNT(*) FROM " + table)
                .query(Integer.class)
                .single();
    }
}

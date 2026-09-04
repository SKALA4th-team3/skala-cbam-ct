package com.skala.cbam.submission.domain;

/** extraction_field 가 어느 normalized_* 컬럼을 쓰는지. ERD 규칙 13: 이 중 하나만 채운다. */
public enum DataValueType {
    TEXT,
    DECIMAL,
    DATE,
    COUNTRY_CODE
}

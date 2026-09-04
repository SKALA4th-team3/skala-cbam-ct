package com.skala.cbam.supplier.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 협력업체 기준정보. API 명세 №1~№4, 요구사항 1~6번.
 *
 * <p>contactEmail 은 단순 연락처가 아니라 <b>수신 메일을 협력업체와 매칭하는 키</b>다(요구사항 1번).
 * 그래서 unique 제약을 걸고, 저장 전에 소문자로 정규화한다 —
 * 메일 주소는 대소문자를 구분하지 않으므로 Kim@a.com 과 kim@a.com 이 서로 다른 업체가 되면
 * 같은 발신자의 메일이 두 업체로 갈린다.
 *
 * <p>countryCode 는 ISO 3166-1 alpha-2 문자열이다. 국가 마스터 테이블은 다른 담당 영역이라
 * 여기서 FK 로 묶지 않는다.
 *
 * <p>시각은 Asia/Seoul(+09:00) 고정이다(공통 규약 5항). 배포 장비의 기본 타임존에 기대지 않도록
 * 명시적으로 서울 기준 OffsetDateTime 을 만들고, 응답 표기가 초 단위이므로 초로 잘라 저장한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "supplier",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_supplier_business_registration_number",
                        columnNames = "business_registration_number"),
                @UniqueConstraint(name = "uk_supplier_contact_email", columnNames = "contact_email")
        },
        indexes = {
                @Index(name = "ix_supplier_name", columnList = "name"),
                @Index(name = "ix_supplier_country_status", columnList = "country_code, status")
        }
)
public class Supplier {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 사업자등록번호. 중복 등록 불가(요구사항 1번). */
    @Column(name = "business_registration_number", nullable = false, length = 40)
    private String businessRegistrationNumber;

    /** 협력업체명. API 응답의 companyName. */
    @Column(name = "name", nullable = false, length = 120)
    private String name;

    /** 협력업체 소재 국가. ISO 3166-1 alpha-2. */
    @Column(name = "country_code", nullable = false, length = 2, columnDefinition = "CHAR(2)")
    private String countryCode;

    @Column(name = "contact_name", nullable = false, length = 60)
    private String contactName;

    /** 담당자 이메일이자 수신 메일 매칭 키(요구사항 1번). 소문자로 저장한다. */
    @Column(name = "contact_email", nullable = false, length = 254)
    private String contactEmail;

    /** 협력업체 전화번호. API 응답의 phone. 선택 입력이다. */
    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SupplierStatus status;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "inactive_at")
    private OffsetDateTime inactiveAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Builder
    private Supplier(String businessRegistrationNumber, String name, String countryCode,
                     String contactName, String contactEmail, String contactPhone) {
        this.businessRegistrationNumber = businessRegistrationNumber;
        this.name = name;
        this.countryCode = countryCode;
        this.contactName = contactName;
        this.contactEmail = normalizeEmail(contactEmail);
        this.contactPhone = contactPhone;
        this.status = SupplierStatus.ACTIVE;
    }

    /** 이메일 비교·저장에 쓰는 정규화. 등록·수정·중복 검사가 모두 이 규칙을 공유해야 한다. */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 담당자 정보 수정 (요구사항 2번).
     * null 인 항목은 건드리지 않는다 — PATCH 는 부분 수정이다(공통 규약 7항).
     */
    public void updateContact(String contactName, String contactEmail, String contactPhone) {
        if (contactName != null) {
            this.contactName = contactName;
        }
        if (contactEmail != null) {
            this.contactEmail = normalizeEmail(contactEmail);
        }
        if (contactPhone != null) {
            this.contactPhone = contactPhone;
        }
    }

    /**
     * 협력 끊김 전환 (요구사항 6번).
     *
     * <p>ERD 무결성 규칙 2번이 INACTIVE 일 때 statusReason 과 inactiveAt 을 필수로 둔다.
     * 사유 없이 끊긴 업체는 나중에 왜 제외됐는지 아무도 설명하지 못하기 때문이다.
     * 사유 검증은 호출부(서비스)가 하고, 여기서는 두 값을 함께 채우는 것만 보장한다.
     */
    public void deactivate(String statusReason) {
        this.status = SupplierStatus.INACTIVE;
        this.statusReason = statusReason;
        this.inactiveAt = now();
    }

    /** 협력 재개. 끊김 사유와 끊긴 시각은 현재 상태를 설명하지 못하므로 함께 지운다. */
    public void activate() {
        this.status = SupplierStatus.ACTIVE;
        this.statusReason = null;
        this.inactiveAt = null;
    }

    @PrePersist
    void onPrePersist() {
        this.createdAt = now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onPreUpdate() {
        this.updatedAt = now();
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(SEOUL).truncatedTo(ChronoUnit.SECONDS);
    }
}

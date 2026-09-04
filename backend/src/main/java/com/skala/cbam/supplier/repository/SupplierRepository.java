package com.skala.cbam.supplier.repository;

import com.skala.cbam.supplier.domain.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 협력업체 기준정보 저장소 (API 명세 №1~№4).
 */
public interface SupplierRepository extends JpaRepository<Supplier, Long>, JpaSpecificationExecutor<Supplier> {

    boolean existsByBusinessRegistrationNumber(String businessRegistrationNumber);

    /** 이메일은 소문자로 저장하므로 호출부가 정규화한 값을 넘겨야 한다. */
    boolean existsByContactEmail(String contactEmail);

    /** 수정 시 중복 검사. 자기 자신은 제외해야 이메일을 그대로 다시 보내도 409가 나지 않는다. */
    boolean existsByContactEmailAndIdNot(String contactEmail, Long id);

}

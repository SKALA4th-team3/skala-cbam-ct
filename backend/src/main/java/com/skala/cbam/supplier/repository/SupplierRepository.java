package com.skala.cbam.supplier.repository;

import com.skala.cbam.supplier.domain.Supplier;
import com.skala.cbam.supplier.domain.SupplierStatus;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 협력업체 기준정보 저장소 (API 명세 №1~№4).
 */
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    boolean existsByBusinessRegistrationNumber(String businessRegistrationNumber);

    /** 이메일은 소문자로 저장하므로 호출부가 정규화한 값을 넘겨야 한다. */
    boolean existsByContactEmail(String contactEmail);

    /** 수정 시 중복 검사. 자기 자신은 제외해야 이메일을 그대로 다시 보내도 409가 나지 않는다. */
    boolean existsByContactEmailAndIdNot(String contactEmail, Long id);

    /**
     * 협력업체 검색 (№3).
     *
     * <p>null 인 조건은 걸지 않는다. 업체명은 대소문자를 가리지 않는 부분 일치다.
     */
    @Query("""
            select s from Supplier s
            where (:search is null or lower(s.name) like lower(concat('%', :search, '%')))
              and (:country is null or s.countryCode = :country)
              and (:status is null or s.status = :status)
            """)
    Page<Supplier> search(@Param("search") String search,
                          @Param("country") String country,
                          @Param("status") SupplierStatus status,
                          Pageable pageable);

    /**
     * 적격 상태 필터가 걸렸을 때의 검색 (№3의 submissionStatus).
     *
     * <p>id 집합을 별도 메서드로 나눈 이유: JPQL 의 in 절에 빈 컬렉션이나 null 을 넘기면
     * 구현체마다 동작이 갈린다. 호출부가 "필터할 id 가 하나 이상 있을 때"만 이 메서드를 쓴다.
     */
    @Query("""
            select s from Supplier s
            where (:search is null or lower(s.name) like lower(concat('%', :search, '%')))
              and (:country is null or s.countryCode = :country)
              and (:status is null or s.status = :status)
              and s.id in :ids
            """)
    Page<Supplier> searchWithinIds(@Param("search") String search,
                                   @Param("country") String country,
                                   @Param("status") SupplierStatus status,
                                   @Param("ids") Collection<Long> ids,
                                   Pageable pageable);
}

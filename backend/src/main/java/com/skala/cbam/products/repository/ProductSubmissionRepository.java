package com.skala.cbam.products.repository;

import com.skala.cbam.submission.domain.Submission;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.Repository;

/** 완제품 계산에 필요한 월별 제출 데이터만 읽는 products 전용 읽기 저장소다. */
public interface ProductSubmissionRepository extends Repository<Submission, Long> {

    List<Submission> findByPartSupplierIdInAndReportingMonthOrderBySubmittedAtDesc(
            Collection<Long> partSupplierIds, LocalDate reportingMonth);
}

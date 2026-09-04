package com.skala.cbam.mail.service.port;

import java.util.List;
import org.springframework.stereotype.Component;

/** CBAM-90(제출 데이터)이 이 브랜치에 병합되기 전까지 쓰는 임시 구현. 항상 빈 목록. */
@Component
class NotYetImplementedMailRelatedDataProvider implements MailRelatedDataProvider {

    @Override
    public List<Long> findSubmissionIds(Long mailReceiptId) {
        return List.of();
    }
}

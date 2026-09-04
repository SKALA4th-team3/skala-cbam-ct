package com.skala.cbam.mail.service.port;

import com.skala.cbam.ai.dto.ExtractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * CBAM-90(제출 데이터)이 병합되기 전까지 쓰는 임시 구현.
 *
 * <p><b>저장하지 않지만 조용히 버리지도 않는다.</b> 무엇을 몇 개 읽었는지 로그로 남긴다 —
 * 분석이 실제로 돌았는지 확인할 길이 그것뿐이기 때문이다. 반환하는 id 는 빈 목록이라
 * №19 의 {@code resourceIds} 도 비고, 화면은 「만들어진 것이 없다」를 정확히 본다.
 */
@Component
class NotYetImplementedAnalysisResultSink implements AnalysisResultSink {

    private static final Logger log = LoggerFactory.getLogger(NotYetImplementedAnalysisResultSink.class);

    @Override
    public Outcome save(Long mailReceiptId, ExtractionResult result) {
        log.info("분석 결과를 저장할 제출 도메인이 아직 없다 (CBAM-90 대기). "
                        + "접수 {} — 항목 {}개, 미등록 부품 {}개: {}",
                mailReceiptId,
                result.items().size(),
                result.unregisteredParts().size(),
                result.items().stream().map(ExtractionResult.Item::key).toList());
        return Outcome.empty();
    }
}

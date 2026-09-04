package com.skala.cbam.task.service.port;

import java.util.List;
import org.springframework.stereotype.Component;

/** CBAM-90(제출 데이터)이 병합되기 전까지 쓰는 임시 구현. 항상 0 — 없는 수를 지어내지 않는다. */
@Component
class NotYetImplementedUnregisteredPartCountProvider implements UnregisteredPartCountProvider {

    @Override
    public int countUnregisteredParts(List<Long> submissionIds) {
        return 0;
    }
}

package com.skala.cbam.supplier.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * 협력업체 수정 · 협력 끊김 요청 (API 명세 №2 · 요구사항 2번 · 6번).
 *
 * <p>PATCH 부분 수정이라 <b>모든 항목이 선택</b>이다. null 인 항목은 건드리지 않는다.
 * status 를 보내면 상태 전이가, 보내지 않으면 정보 수정만 일어난다.
 *
 * <p>status 를 enum 이 아니라 String 으로 받는 이유: 허용값이 아닐 때 명세가 요구하는
 * 400 INVALID_STATUS 를 내려면 Jackson 의 enum 바인딩 실패보다 먼저 우리가 판별해야 한다.
 */
public record SupplierUpdateRequest(

        @Size(max = 60, message = "담당자명은 60자를 넘을 수 없습니다")
        String contactName,

        @Email(message = "담당자 이메일 형식이 올바르지 않습니다")
        @Size(max = 254, message = "담당자 이메일은 254자를 넘을 수 없습니다")
        String contactEmail,

        @Size(max = 30, message = "전화번호는 30자를 넘을 수 없습니다")
        String phone,

        String status,

        @Size(max = 500, message = "상태 변경 사유는 500자를 넘을 수 없습니다")
        String statusReason
) {
}

package com.skala.cbam.supplier.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 목록 API 공통 페이징 응답 (API 명세 v10 공통 규약 4항).
 *
 * <p>"목록 API는 예외 없이 아래 5개 키를 반환한다" — content · page · size · totalElements · totalPages.
 * Spring 의 Page 를 그대로 직렬화하면 키 이름과 개수가 달라지므로 여기서 고정한다.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <E, T> PageResponse<T> of(Page<E> page, List<T> content) {
        return new PageResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}

package com.skala.cbam.parts.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 페이징 응답 공통 규약(개요·공통 규약 시트 4항) 래퍼.
 * 다른 팀원이 global에 공통 버전을 올릴 예정이라 그 전까지 parts 안에서만 쓴다.
 * TODO(global 공통 인프라 머지 후): 이 클래스는 지우고 global의 공통 PageResponse로 교체한다.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}

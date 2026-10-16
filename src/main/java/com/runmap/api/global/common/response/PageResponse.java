package com.runmap.api.global.common.response;

import java.util.List;
import org.springframework.data.domain.Page;

/** 목록 응답을 감싸는 얇은 페이지 포맷. 단건/생성 등 나머지 성공 응답은 DTO를 그대로 반환한다. */
public record PageResponse<T>(
    List<T> content, int page, int size, long totalElements, int totalPages, boolean last) {
  public static <T> PageResponse<T> from(Page<T> page) {
    return new PageResponse<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.isLast());
  }
}

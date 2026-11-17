package com.runmap.api.map.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * GET /api/v1/dong 응답. 좌표가 속한 행정동.
 *
 * <p>{@code code} 는 정복률과 같은 10자리 행정동 코드라, 앱이 이 값으로 정복률 데이터와 바로 매칭할 수 있다.
 */
@Schema(description = "좌표가 속한 행정동")
public record DongResponse(
    @Schema(description = "10자리 행정동 코드 (정복률과 동일 체계)", example = "1111053000") String code,
    @Schema(description = "5자리 시군구 코드", example = "11110") String gu,
    @Schema(description = "행정동 이름", example = "서울특별시 종로구 사직동") String name) {}

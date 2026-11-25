package com.runmap.api.map.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * GET /api/v1/dong 응답. 좌표가 속한 행정동과 가까운 도로명.
 *
 * <p>{@code code} 는 정복률과 같은 10자리 행정동 코드라, 앱이 이 값으로 정복률 데이터와 바로 매칭할 수 있다. {@code road} 는 출발·도착 지점
 * 표시용 도로명이며, 근처에 이름 있는 도로가 없으면 null — 이 경우 앱은 동네 이름({@code name})으로 대체한다.
 */
@Schema(description = "좌표가 속한 행정동과 가까운 도로명")
public record DongResponse(
    @Schema(description = "10자리 행정동 코드 (정복률과 동일 체계)", example = "1111053000") String code,
    @Schema(description = "5자리 시군구 코드", example = "11110") String gu,
    @Schema(description = "행정동 이름", example = "서울특별시 종로구 사직동") String name,
    @Schema(description = "가까운 도로명. 근처에 이름 있는 도로가 없으면 null", example = "사직로", nullable = true)
        String road) {}

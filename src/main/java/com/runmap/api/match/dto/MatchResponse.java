package com.runmap.api.match.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/**
 * POST /api/v1/match 응답 (API 명세 §3.1).
 *
 * @param segmentIds 달린 것으로 인정된 구간 ID. 중복 없음
 * @param matchedPath 도로에 맞춘 경로선 [lng, lat]
 * @param sigunguCodes 인정 구간이 속한 구 코드
 * @param dongBySegment 인정 구간 ID → 그 구간이 속한 동(행정동) 코드. 동 단위 정복률 계산용
 */
@Schema(description = "맵매칭 결과. 서버에 저장하지 않는다(판정만).")
public record MatchResponse(
    @Schema(description = "매칭에 사용된 지도 버전", example = "2026-11-10") String mapVersion,
    @Schema(
            description =
                "달린 것으로 인정된 구간(segment) ID 목록, 중복 없음."
                    + " 매우 큰 64비트 정수이므로 클라이언트(JavaScript)에서는 문자열로 처리하기를 권장한다.",
            example = "[1180579831973998526, 8844221093002117]")
        List<Long> segmentIds,
    @Schema(description = "도로에 맞춘 보정 경로선. 각 점은 [경도(lng), 위도(lat)].") List<double[]> matchedPath,
    @Schema(description = "매칭된 총 거리(m)", example = "1840") int matchedDistanceM,
    @Schema(description = "인정 구간이 지나간 시군구 코드(5자리) 목록", example = "[\"11440\"]")
        List<String> sigunguCodes,
    @Schema(
            description = "인정 구간 ID → 그 구간이 속한 행정동 코드(10자리). 동 단위 정복률 계산에 쓴다.",
            example = "{\"1180579831973998526\":\"1144051500\"}")
        Map<Long, String> dongBySegment) {}

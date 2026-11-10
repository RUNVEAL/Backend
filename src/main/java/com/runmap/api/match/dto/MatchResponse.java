package com.runmap.api.match.dto;

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
public record MatchResponse(
    String mapVersion,
    List<Long> segmentIds,
    List<double[]> matchedPath,
    int matchedDistanceM,
    List<String> sigunguCodes,
    Map<Long, String> dongBySegment) {}

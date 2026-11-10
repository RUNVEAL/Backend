package com.runmap.api.match.dto;

import java.util.List;

/**
 * POST /api/v1/match 응답 (API 명세 §3.1).
 *
 * @param segmentIds 달린 것으로 인정된 구간 ID. 중복 없음
 * @param matchedPath 도로에 맞춘 경로선 [lng, lat]
 * @param sigunguCodes 인정 구간이 속한 구 코드
 */
public record MatchResponse(
    String mapVersion,
    List<Long> segmentIds,
    List<double[]> matchedPath,
    int matchedDistanceM,
    List<String> sigunguCodes) {}

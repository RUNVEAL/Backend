package com.runmap.api.route.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** POST /api/v1/routes 응답 (API 명세 §3.2). 결과 0개는 에러가 아니다: routes [] + warnings. */
public record RouteRecommendResponse(
    String mapVersion,
    List<RouteDto> routes,
    List<String> warnings,
    @JsonInclude(JsonInclude.Include.NON_NULL) Integer shortestDistanceM) {

  public static final String NO_ROUTE_FOUND = "NO_ROUTE_FOUND";
  public static final String DESTINATION_FARTHER = "DESTINATION_FARTHER_THAN_DISTANCE";

  /**
   * @param path [lng, lat] 소수 6자리
   * @param elevationProfile [누적거리m, 고도m], 고도 데이터가 없으면 null
   * @param newRoadM exploredSegmentIds 를 안 보냈으면 null
   */
  public record RouteDto(
      String routeId,
      String type,
      int distanceM,
      int durationSec,
      Integer elevationGainM,
      List<int[]> elevationProfile,
      Integer newRoadM,
      Double newRoadRatio,
      List<String> tags,
      List<double[]> path,
      List<Long> segmentIds,
      List<MilestoneDto> milestones) {}

  public record MilestoneDto(String name, String kind, int startM, int endM) {}
}

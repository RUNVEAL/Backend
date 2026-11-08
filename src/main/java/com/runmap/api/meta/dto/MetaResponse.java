package com.runmap.api.meta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** GET /api/v1/meta 응답 (API 명세 §3.3). 서버가 실제로 탑재한 지도 버전과 검증 한도. */
public record MetaResponse(
    String build, MapVersions mapVersions, Limits limits, RouteOptions routes) {

  public record MapVersions(String current, List<String> supported) {}

  public record Limits(int maxBodyBytes, MatchLimits match, RouteLimits routes) {}

  public record MatchLimits(int minPoints, int maxPoints) {}

  public record RouteLimits(
      Range distanceM, Range paceSecPerKm, int maxExploredSegmentIds, int maxRoutes) {}

  public record Range(int min, int max, Integer step, @JsonProperty("default") int defaultValue) {}

  public record RouteOptions(
      List<String> filters,
      List<String> tags,
      double loopToleranceRatio,
      double oneWayToleranceRatio) {}
}

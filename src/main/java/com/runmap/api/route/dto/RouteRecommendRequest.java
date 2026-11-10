package com.runmap.api.route.dto;

import com.runmap.api.global.error.BusinessException;
import com.runmap.api.global.error.ErrorCode;
import com.runmap.api.map.domain.MapVersions;
import com.runmap.api.route.domain.RouteFilter;
import java.util.List;

/**
 * POST /api/v1/routes 요청 (API 명세 §3.2). end 가 null 이면 순환, 있으면 편도.
 *
 * <p>exploredSegmentIds 는 null(안 보냄)과 [](탐험 없음)을 구분한다: null 이면 새 길 거리를 계산하지 않는다.
 */
public record RouteRecommendRequest(
    String mapVersion,
    Point start,
    Point end,
    Integer distanceM,
    Boolean preferUnexplored,
    List<Long> exploredSegmentIds,
    List<RouteFilter> filters,
    Integer paceSecPerKm) {

  public static final int MIN_DISTANCE_M = 1000;
  public static final int MAX_DISTANCE_M = 21000;
  public static final int DISTANCE_STEP_M = 500;
  public static final int DEFAULT_DISTANCE_M = 5000;
  public static final int MIN_PACE = 180;
  public static final int MAX_PACE = 900;
  public static final int DEFAULT_PACE = 390;
  public static final int MAX_EXPLORED = 10_000;

  public record Point(Double lat, Double lng) {}

  public RouteRecommendRequest {
    distanceM = distanceM == null ? DEFAULT_DISTANCE_M : distanceM;
    preferUnexplored = preferUnexplored == null || preferUnexplored;
    filters = filters == null ? List.of() : filters;
    paceSecPerKm = paceSecPerKm == null ? DEFAULT_PACE : paceSecPerKm;
  }

  /** 첫 위반 1건만 던진다 (API 명세 §2.3). 지원 버전(409)은 컨트롤러가 따로 본다. */
  public void validate() {
    MapVersions.requireFormat(mapVersion);
    checkPoint("start", start);
    if (end != null) {
      checkPoint("end", end);
    }
    if (distanceM < MIN_DISTANCE_M || distanceM > MAX_DISTANCE_M) {
      throw BusinessException.invalid("distanceM", "OUT_OF_RANGE");
    }
    if (distanceM % DISTANCE_STEP_M != 0) {
      throw BusinessException.invalid("distanceM", "NOT_MULTIPLE_OF_STEP");
    }
    if (paceSecPerKm < MIN_PACE || paceSecPerKm > MAX_PACE) {
      throw BusinessException.invalid("paceSecPerKm", "OUT_OF_RANGE");
    }
    if (exploredSegmentIds != null && exploredSegmentIds.size() > MAX_EXPLORED) {
      throw new BusinessException(ErrorCode.TOO_MANY_SEGMENTS);
    }
  }

  private static void checkPoint(String name, Point p) {
    if (p == null) {
      throw BusinessException.invalid(name, "REQUIRED");
    }
    checkCoord(name + ".lat", p.lat(), 90);
    checkCoord(name + ".lng", p.lng(), 180);
  }

  private static void checkCoord(String field, Double v, double limit) {
    if (v == null) {
      throw BusinessException.invalid(field, "REQUIRED");
    }
    if (!Double.isFinite(v)) {
      throw BusinessException.invalid(field, "NOT_FINITE");
    }
    if (Math.abs(v) > limit) {
      throw BusinessException.invalid(field, "OUT_OF_RANGE");
    }
  }
}

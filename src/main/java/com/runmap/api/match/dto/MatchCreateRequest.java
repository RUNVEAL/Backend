package com.runmap.api.match.dto;

import com.runmap.api.global.error.BusinessException;
import com.runmap.api.global.error.ErrorCode;
import com.runmap.api.map.domain.MapVersions;
import com.runmap.api.match.domain.MatchPurpose;
import java.util.List;

/**
 * POST /api/v1/match 요청 (API 명세 §3.1). points 는 [lng, lat] 배열, 앱이 이미 자르고 10초로 줄인 것.
 *
 * <p>필드 의미(점 개수·인정 규칙)의 주인은 우선님(계약 C4).
 */
public record MatchCreateRequest(String mapVersion, MatchPurpose purpose, List<double[]> points) {

  public static final int MIN_POINTS = 10;
  public static final int MAX_POINTS = 2000;

  /** 첫 위반 1건만 던진다. 지원 버전(409)은 컨트롤러가 따로 본다. */
  public void validate() {
    MapVersions.requireFormat(mapVersion);
    if (purpose == null) {
      throw BusinessException.invalid("purpose", "REQUIRED");
    }
    if (points == null) {
      throw BusinessException.invalid("points", "REQUIRED");
    }
    for (int i = 0; i < points.size(); i++) {
      checkPoint("points[" + i + "]", points.get(i));
    }
    if (points.size() < MIN_POINTS) {
      throw BusinessException.invalid("points", "TOO_FEW");
    }
    if (points.size() > MAX_POINTS) {
      throw new BusinessException(ErrorCode.TOO_MANY_POINTS);
    }
  }

  private static void checkPoint(String field, double[] p) {
    if (p == null || p.length != 2) {
      throw BusinessException.invalid(field, "INVALID_TYPE");
    }
    if (!Double.isFinite(p[0]) || !Double.isFinite(p[1])) {
      throw BusinessException.invalid(field, "NOT_FINITE");
    }
    if (Math.abs(p[0]) > 180 || Math.abs(p[1]) > 90) {
      throw BusinessException.invalid(field, "OUT_OF_RANGE");
    }
  }
}

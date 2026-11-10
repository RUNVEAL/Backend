package com.runmap.api.route.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 코스 태그: 실제로 충족한 것만, 요청한 필터 먼저, 최대 3개 (API 명세 §3.2 태그 기준). */
public final class RouteTags {

  static final int MAX_TAGS = 3;
  static final double AREA_SHARE = 0.2; // 공원·하천 구간 ≥ 코스의 20%
  static final double STEEP_GRADE = 0.06; // 100m 평균 경사 6%
  static final double FLAT_GAIN_PER_KM = 5; // 상승 고도 ≤ 5m/km

  private RouteTags() {}

  public static List<String> of(
      int distanceM,
      Integer elevationGainM,
      List<int[]> profile,
      double parkM,
      double riverM,
      List<RouteFilter> requested) {
    Set<RouteTag> met = new LinkedHashSet<>();
    if (parkM >= distanceM * AREA_SHARE) {
      met.add(RouteTag.PARK);
    }
    if (riverM >= distanceM * AREA_SHARE) {
      met.add(RouteTag.RIVERSIDE);
    }
    if (elevationGainM != null && profile != null) {
      if (elevationGainM <= FLAT_GAIN_PER_KM * distanceM / 1000) {
        met.add(RouteTag.FLAT);
      } else if (!hasSteep(profile)) {
        met.add(RouteTag.GENTLE_SLOPE);
      }
    }
    Set<RouteTag> ordered = new LinkedHashSet<>();
    requested.stream()
        .map(f -> RouteTag.valueOf(f.name()))
        .filter(met::contains)
        .forEach(ordered::add);
    ordered.addAll(met);
    return ordered.stream().limit(MAX_TAGS).map(Enum::name).toList();
  }

  private static boolean hasSteep(List<int[]> profile) {
    for (int i = 1; i < profile.size(); i++) {
      int run = profile.get(i)[0] - profile.get(i - 1)[0];
      int rise = Math.abs(profile.get(i)[1] - profile.get(i - 1)[1]);
      if (run > 0 && (double) rise / run > STEEP_GRADE) {
        return true;
      }
    }
    return false;
  }
}

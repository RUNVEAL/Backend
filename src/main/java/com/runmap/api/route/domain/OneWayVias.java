package com.runmap.api.route.domain;

import com.graphhopper.util.shapes.GHPoint;
import java.util.ArrayList;
import java.util.List;

/**
 * 편도 우회 경유지: A·B 를 초점으로 하는 타원 위의 점 8개. 직선 거리 합 |AW|+|WB| = 목표 거리 / 도로 우회계수.
 *
 * <p>PoC bench.py bench_dest_distance 이식 (목표 대비 오차 p50 1.8%).
 */
public final class OneWayVias {

  static final int COUNT = 8;
  // ponytail: 도로가 직선보다 25% 길다는 근사. 서울 그래프 eval 후 조정
  static final double DETOUR_FACTOR = 1.25;
  private static final double M_PER_DEG_LAT = 111_320;

  private OneWayVias() {}

  public static List<GHPoint> of(GHPoint a, GHPoint b, int distanceM) {
    double mPerDegLng = M_PER_DEG_LAT * Math.cos(Math.toRadians(a.lat));
    double bx = (b.lon - a.lon) * mPerDegLng;
    double by = (b.lat - a.lat) * M_PER_DEG_LAT;
    double semi = distanceM / DETOUR_FACTOR / 2;
    double focal = Math.hypot(bx, by) / 2;
    double minor = Math.sqrt(Math.max(semi * semi - focal * focal, 1));
    double angle = Math.atan2(by, bx);
    List<GHPoint> vias = new ArrayList<>(COUNT);
    for (int k = 0; k < COUNT; k++) {
      double t = 2 * Math.PI * k / COUNT;
      double ex = semi * Math.cos(t);
      double ey = minor * Math.sin(t);
      double wx = bx / 2 + ex * Math.cos(angle) - ey * Math.sin(angle);
      double wy = by / 2 + ex * Math.sin(angle) + ey * Math.cos(angle);
      vias.add(new GHPoint(a.lat + wy / M_PER_DEG_LAT, a.lon + wx / mPerDegLng));
    }
    return vias;
  }
}

package com.runmap.api.route.domain;

import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.PointList;
import java.util.ArrayList;
import java.util.List;

/** 고도 그래프 [누적거리m, 고도m]. 첫 점 0, 끝 점 총 거리, 간격 ≤ step (D10). 고도가 하나라도 없으면 null → 앱이 그래프를 숨긴다. */
public final class ElevationProfile {

  private static final DistanceCalc DIST = DistanceCalcEarth.DIST_EARTH;

  private ElevationProfile() {}

  public static List<int[]> sample(PointList pl, int stepM) {
    if (!pl.is3D() || pl.isEmpty()) {
      return null;
    }
    double[] cum = new double[pl.size()];
    for (int i = 0; i < pl.size(); i++) {
      if (Double.isNaN(pl.getEle(i))) {
        return null;
      }
      if (i > 0) {
        cum[i] =
            cum[i - 1]
                + DIST.calcDist(pl.getLat(i - 1), pl.getLon(i - 1), pl.getLat(i), pl.getLon(i));
      }
    }
    double total = cum[cum.length - 1];
    List<int[]> out = new ArrayList<>();
    int seg = 0;
    for (double d = 0; d < total - 0.5; d += stepM) {
      while (cum[seg + 1] < d) {
        seg++;
      }
      double span = cum[seg + 1] - cum[seg];
      double t = span == 0 ? 0 : (d - cum[seg]) / span;
      double ele = pl.getEle(seg) + t * (pl.getEle(seg + 1) - pl.getEle(seg));
      out.add(new int[] {(int) Math.round(d), (int) Math.round(ele)});
    }
    out.add(new int[] {(int) Math.round(total), (int) Math.round(pl.getEle(pl.size() - 1))});
    return out;
  }
}

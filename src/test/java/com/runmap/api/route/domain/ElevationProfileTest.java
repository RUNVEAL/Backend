package com.runmap.api.route.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.graphhopper.util.PointList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ElevationProfileTest {

  @Test
  void samplesEvery100mWithEnds() {
    PointList pl = new PointList(3, true);
    pl.add(37.5000, 126.9000, 10);
    pl.add(37.5000, 126.9034, 20); // 약 300m
    pl.add(37.5000, 126.9068, 10);
    List<int[]> p = ElevationProfile.sample(pl, 100);
    assertThat(p.get(0)).containsExactly(0, 10);
    assertThat(p.get(p.size() - 1)[0]).isCloseTo(600, within(5));
    assertThat(p.get(p.size() - 1)[1]).isEqualTo(10);
    for (int i = 1; i < p.size(); i++) {
      assertThat(p.get(i)[0] - p.get(i - 1)[0]).isLessThanOrEqualTo(100);
    }
  }

  @Test
  void nullWhenAnyElevationMissing() {
    PointList pl = new PointList(2, true);
    pl.add(37.5, 126.9, 10);
    pl.add(37.5, 126.901, Double.NaN);
    assertThat(ElevationProfile.sample(pl, 100)).isNull();
  }

  @Test
  void nullWhenNot3d() {
    PointList pl = new PointList(2, false);
    pl.add(37.5, 126.9);
    pl.add(37.5, 126.901);
    assertThat(ElevationProfile.sample(pl, 100)).isNull();
  }
}

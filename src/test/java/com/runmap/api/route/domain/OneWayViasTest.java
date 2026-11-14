package com.runmap.api.route.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withinPercentage;

import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.shapes.GHPoint;
import java.util.List;
import org.junit.jupiter.api.Test;

class OneWayViasTest {

  @Test
  void viasLieOnEllipseWithFociAtStartAndEnd() {
    GHPoint a = new GHPoint(37.5552, 126.9236);
    GHPoint b = new GHPoint(37.5552, 126.9326);
    List<GHPoint> vias = OneWayVias.of(a, b, 3000);
    assertThat(vias).hasSize(8);
    DistanceCalc dc = DistanceCalcEarth.DIST_EARTH;
    for (GHPoint w : vias) {
      double sum =
          dc.calcDist(a.lat, a.lon, w.lat, w.lon) + dc.calcDist(w.lat, w.lon, b.lat, b.lon);
      assertThat(sum).isCloseTo(3000 / 1.25, withinPercentage(2));
    }
  }
}

package com.runmap.api.route.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RouteTagsTest {

  @Test
  void flatWinsOverGentleAndRequestedFirstMax3() {
    List<int[]> flat = List.of(new int[] {0, 10}, new int[] {100, 10}, new int[] {5000, 12});
    // 12m ≤ 5m/km × 5km → FLAT, GENTLE_SLOPE 없음
    assertThat(RouteTags.of(5000, 12, flat, 2000, 1500, List.of(RouteFilter.RIVERSIDE)))
        .containsExactly("RIVERSIDE", "PARK", "FLAT");
  }

  @Test
  void gentleWhenNoSteepSampleButNotFlat() {
    List<int[]> gentle = List.of(new int[] {0, 10}, new int[] {100, 15}, new int[] {3000, 40});
    assertThat(RouteTags.of(3000, 40, gentle, 0, 0, List.of())).containsExactly("GENTLE_SLOPE");
  }

  @Test
  void steepSegmentBlocksGentle() {
    List<int[]> steep = List.of(new int[] {0, 10}, new int[] {100, 17}, new int[] {3000, 40});
    assertThat(RouteTags.of(3000, 40, steep, 0, 0, List.of()))
        .doesNotContain("GENTLE_SLOPE", "FLAT");
  }

  @Test
  void noElevationNoSlopeTags() {
    assertThat(RouteTags.of(3000, null, null, 0, 0, List.of(RouteFilter.GENTLE_SLOPE))).isEmpty();
  }

  @Test
  void parkNeeds20Percent() {
    assertThat(RouteTags.of(5000, null, null, 999, 0, List.of())).isEmpty();
    assertThat(RouteTags.of(5000, null, null, 1000, 0, List.of())).containsExactly("PARK");
  }
}

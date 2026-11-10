package com.runmap.api.map.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MapIndexTest {

  @Test
  void roundsTotalsToMeters() {
    RegionTotals totals = new RegionTotals(Map.of("A", 123.4, "B", 456.6), Map.of("G1", 580.0));

    MapIndex index = MapIndex.from("2026-10-27", totals);

    assertThat(index.version()).isEqualTo("2026-10-27");
    assertThat(index.dongLengthM()).containsEntry("A", 123L).containsEntry("B", 457L);
    assertThat(index.guLengthM()).containsEntry("G1", 580L);
  }
}

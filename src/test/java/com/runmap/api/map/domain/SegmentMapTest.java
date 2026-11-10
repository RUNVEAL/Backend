package com.runmap.api.map.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SegmentMapTest {

  @Test
  void loadedMapResolvesSegmentsBackToTheirEdges() {
    SegmentMap map = SegmentMap.of(new long[] {5L, 5L, 99L});

    assertThat(map.segmentOf(0)).isEqualTo(5L);
    assertThat(map.segmentOf(1)).isEqualTo(5L);
    assertThat(map.segmentOf(2)).isEqualTo(99L);
    assertThat(map.edgesOf(List.of(5L))).containsExactlyInAnyOrder(0, 1);
    assertThat(map.edgesOf(List.of(99L, 123_456L))).containsExactly(2); // 모르는 id 무시
  }

  @Test
  void identityMapMatchesEdgeIdsWithinRange() {
    SegmentMap map = SegmentMap.identity(3);

    assertThat(map.segmentOf(1)).isEqualTo(1L);
    assertThat(map.segmentOf(5)).isEqualTo(-1L); // 범위 밖
    assertThat(map.edgesOf(List.of(0L, 1L, 5L))).containsExactlyInAnyOrder(0, 1); // 5 범위 밖 무시
  }
}

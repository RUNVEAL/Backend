package com.runmap.api.map.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.graphhopper.GraphHopper;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeIteratorState;
import com.runmap.api.map.domain.AdminAreas;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RegionTotalsTest {

  @Autowired GraphHopper gh;

  @Test
  void sumsRunnableLengthPerDongAndGu() {
    AdminAreas areas = new AdminAreas("src/test/resources/fixtures/admin");

    RegionTotals totals = RegionTotals.compute(gh, areas, SegmentBuilder.build(gh));

    assertThat(totals.byDong()).containsKey("B"); // 서교동 중심이 동 B
    double dongSum = totals.byDong().values().stream().mapToDouble(Double::doubleValue).sum();
    assertThat(totals.byGu().get("G1")).isCloseTo(dongSum, within(1.0)); // 구 = 그 구의 동 합
    assertThat(totals.byGu().get("G1")).isGreaterThan(0.0);
  }

  /**
   * 정복률 분모(dongLengthM)는 분자와 같은 규칙으로 배정돼야 한다: 각 segment의 전체 길이를 그 segment centroid가 속한 동 하나에 통째로.
   * edge별로 쪼개 배정하면 경계를 가로지르는 segment에서 분자(segment 단위)와 어긋나 완전 정복도 100%가 안 된다.
   *
   * <p>좁은 세로 띠 경계를 깔아 segment들이 경계를 확실히 가로지르게 만든 뒤, 분모가 segment 단위로 배정되는지 검증한다.
   */
  @Test
  void attributesEachSegmentWholeToOneDong(@TempDir Path dir) throws IOException {
    writeStripAreas(dir); // lng 0.0015deg(~130m) 폭 세로 띠 — segment보다 좁아 가로지름 발생
    AdminAreas areas = new AdminAreas(dir.toString());
    long[] segByEdge = SegmentBuilder.build(gh);
    BaseGraph graph = gh.getBaseGraph();
    NodeAccess nodes = graph.getNodeAccess();

    // 기대값: segment를 통째로, centroid가 속한 동에 (분자와 동일한 규칙) — 독립 계산
    Map<Long, List<Integer>> bySegment = new HashMap<>();
    for (int edge = 0; edge < segByEdge.length; edge++) {
      if (segByEdge[edge] >= 0) {
        bySegment.computeIfAbsent(segByEdge[edge], k -> new ArrayList<>()).add(edge);
      }
    }
    Map<String, Double> expected = new HashMap<>();
    bySegment.forEach(
        (segment, edges) -> {
          double length = 0;
          double lat = 0;
          double lng = 0;
          for (int edge : edges) {
            EdgeIteratorState s = graph.getEdgeIteratorState(edge, Integer.MIN_VALUE);
            length += s.getDistance();
            lat += nodes.getLat(s.getBaseNode()) + nodes.getLat(s.getAdjNode());
            lng += nodes.getLon(s.getBaseNode()) + nodes.getLon(s.getAdjNode());
          }
          int points = edges.size() * 2;
          double total = length;
          areas
              .codeAt(lat / points, lng / points)
              .ifPresent(d -> expected.merge(d, total, Double::sum));
        });

    RegionTotals totals = RegionTotals.compute(gh, areas, segByEdge);

    assertThat(totals.byDong().keySet()).isEqualTo(expected.keySet());
    expected.forEach(
        (dong, length) -> assertThat(totals.byDong().get(dong)).isCloseTo(length, within(0.5)));
  }

  /** 서교동 bbox를 덮는 좁은 세로 띠 동들(gu=G1). 폭이 segment보다 좁아 경계 가로지름이 반드시 생긴다. */
  private static void writeStripAreas(Path dir) throws IOException {
    double loLng = 126.880;
    double hiLng = 126.950;
    double loLat = 37.520;
    double hiLat = 37.580;
    double width = 0.0015;
    StringBuilder sb = new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[");
    int i = 0;
    for (double x = loLng; x < hiLng; x += width, i++) {
      double x2 = x + width;
      if (i > 0) {
        sb.append(',');
      }
      sb.append("{\"type\":\"Feature\",\"properties\":{\"code\":\"S")
          .append(i)
          .append("\",\"gu\":\"G1\"},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[")
          .append("[")
          .append(x)
          .append(',')
          .append(loLat)
          .append("],")
          .append("[")
          .append(x2)
          .append(',')
          .append(loLat)
          .append("],")
          .append("[")
          .append(x2)
          .append(',')
          .append(hiLat)
          .append("],")
          .append("[")
          .append(x)
          .append(',')
          .append(hiLat)
          .append("],")
          .append("[")
          .append(x)
          .append(',')
          .append(loLat)
          .append("]")
          .append("]]}}");
    }
    sb.append("]}");
    Files.writeString(dir.resolve("dong.geojson"), sb.toString());
  }
}

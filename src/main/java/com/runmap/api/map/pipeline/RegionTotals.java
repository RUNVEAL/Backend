package com.runmap.api.map.pipeline;

import com.graphhopper.GraphHopper;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.runmap.api.map.domain.AdminAreas;
import com.runmap.api.map.domain.Segments;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 행정동·구별 달릴 수 있는 길의 총 길이(m) — 정복률의 분모. 각 segment 전체 길이를 그 segment centroid가 속한 동에 통째로 더한다.
 *
 * <p>분자({@code MatchService}의 구간→동)와 같은 규칙({@link Segments#centroid})을 써야 경계를 가로지르는 구간에서도 분모·분자가
 * 어긋나지 않는다. edge별로 쪼개 배정하면 완전 정복도 100%가 안 된다.
 *
 * @param byDong 행정동 코드 → 총 길이(m)
 * @param byGu 구 코드 → 총 길이(m) (그 구에 속한 행정동들의 합)
 */
public record RegionTotals(Map<String, Double> byDong, Map<String, Double> byGu) {

  public static RegionTotals compute(GraphHopper gh, AdminAreas areas, long[] segmentIdByEdge) {
    BaseGraph graph = gh.getBaseGraph();
    NodeAccess nodes = graph.getNodeAccess();

    // edge를 segment로 묶는다 (정복 대상이 아닌 -1 은 제외)
    Map<Long, List<Integer>> bySegment = new HashMap<>();
    for (int edge = 0; edge < segmentIdByEdge.length; edge++) {
      long segment = segmentIdByEdge[edge];
      if (segment >= 0) {
        bySegment.computeIfAbsent(segment, key -> new ArrayList<>()).add(edge);
      }
    }

    Map<String, Double> byDong = new HashMap<>();
    Map<String, Double> byGu = new HashMap<>();
    for (List<Integer> edges : bySegment.values()) {
      double lengthM = 0;
      for (int edge : edges) {
        lengthM += graph.getEdgeIteratorState(edge, Integer.MIN_VALUE).getDistance();
      }
      double[] center = Segments.centroid(graph, nodes, edges);
      double total = lengthM;
      areas.codeAt(center[0], center[1]).ifPresent(dong -> byDong.merge(dong, total, Double::sum));
      areas.guAt(center[0], center[1]).ifPresent(gu -> byGu.merge(gu, total, Double::sum));
    }
    return new RegionTotals(byDong, byGu);
  }
}

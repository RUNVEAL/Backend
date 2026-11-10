package com.runmap.api.map.pipeline;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.runmap.api.map.domain.AdminAreas;
import java.util.HashMap;
import java.util.Map;

/**
 * 행정동·구별 달릴 수 있는 길의 총 길이(m) — 정복률의 분모. edge를 midpoint로 행정동에 배정해 길이를 합산한다.
 *
 * @param byDong 행정동 코드 → 총 길이(m)
 * @param byGu 구 코드 → 총 길이(m) (그 구에 속한 행정동들의 합)
 */
public record RegionTotals(Map<String, Double> byDong, Map<String, Double> byGu) {

  public static RegionTotals compute(GraphHopper gh, AdminAreas areas) {
    BaseGraph graph = gh.getBaseGraph();
    NodeAccess nodes = graph.getNodeAccess();
    Map<String, Double> byDong = new HashMap<>();
    Map<String, Double> byGu = new HashMap<>();

    AllEdgesIterator edge = graph.getAllEdges();
    while (edge.next()) {
      int base = edge.getBaseNode();
      int adj = edge.getAdjNode();
      double lat = (nodes.getLat(base) + nodes.getLat(adj)) / 2;
      double lng = (nodes.getLon(base) + nodes.getLon(adj)) / 2;
      double lengthM = edge.getDistance();

      areas.codeAt(lat, lng).ifPresent(dong -> byDong.merge(dong, lengthM, Double::sum));
      areas.guAt(lat, lng).ifPresent(gu -> byGu.merge(gu, lengthM, Double::sum));
    }
    return new RegionTotals(byDong, byGu);
  }
}

package com.runmap.api.map.domain;

import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeIteratorState;
import java.util.Collection;

/**
 * 구간(segment) 지오메트리 도우미.
 *
 * <p>정복률 분모(파이프라인 {@code RegionTotals})와 분자({@code MatchService}의 구간→동)는 구간을 동에 배정할 때 반드시 같은 규칙을 써야
 * 한다. 그래야 경계를 가로지르는 구간에서도 어긋나지 않고, 완전 정복이 정확히 100%가 된다. 그 공통 규칙이 이 centroid다.
 */
public final class Segments {

  private Segments() {}

  /** 구간 edge들의 노드 좌표 평균 [lat, lng]. 이 centroid가 속한 동 하나로 구간 전체를 배정한다. */
  public static double[] centroid(BaseGraph graph, NodeAccess nodes, Collection<Integer> edges) {
    double lat = 0;
    double lng = 0;
    for (int edge : edges) {
      EdgeIteratorState state = graph.getEdgeIteratorState(edge, Integer.MIN_VALUE);
      lat += nodes.getLat(state.getBaseNode()) + nodes.getLat(state.getAdjNode());
      lng += nodes.getLon(state.getBaseNode()) + nodes.getLon(state.getAdjNode());
    }
    int points = edges.size() * 2;
    return new double[] {lat / points, lng / points};
  }
}

package com.runmap.api.map.pipeline;

import com.graphhopper.GraphHopper;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 그래프의 edge를 교차점 사이 체인으로 묶어 결정론적 segment 번호를 만든다 (segments.bin 생성용, A 소유).
 *
 * <p>교차점·막다른 노드(degree != 2)가 체인의 경계다. 통과 노드(degree == 2)로만 이어지는 edge들은 한 segment로 묶인다. 같은 OSM
 * 스냅샷이면 항상 같은 번호가 나와야 한다.
 */
public final class SegmentBuilder {

  private SegmentBuilder() {}

  /** edgeId → segmentId 매핑. */
  public static long[] build(GraphHopper gh) {
    BaseGraph graph = gh.getBaseGraph();
    int edges = graph.getEdges();
    long[] segmentIdByEdge = new long[edges];
    Arrays.fill(segmentIdByEdge, -1L);

    EdgeExplorer explorer = graph.createEdgeExplorer();
    long nextId = 0;
    for (int edge = 0; edge < edges; edge++) {
      if (segmentIdByEdge[edge] != -1L) {
        continue;
      }
      long id = nextId++;
      for (int chainEdge : chainOf(graph, explorer, edge)) {
        segmentIdByEdge[chainEdge] = id;
      }
    }
    return segmentIdByEdge;
  }

  /** edge가 속한, 교차점 사이 체인의 모든 edge. */
  private static List<Integer> chainOf(BaseGraph graph, EdgeExplorer explorer, int startEdge) {
    List<Integer> chain = new ArrayList<>();
    Set<Integer> seen = new HashSet<>();
    chain.add(startEdge);
    seen.add(startEdge);

    EdgeIteratorState start = graph.getEdgeIteratorState(startEdge, Integer.MIN_VALUE);
    extend(explorer, start.getBaseNode(), startEdge, chain, seen);
    extend(explorer, start.getAdjNode(), startEdge, chain, seen);
    return chain;
  }

  /** 통과 노드(degree == 2)로만 이어지는 동안 체인을 늘린다. */
  private static void extend(
      EdgeExplorer explorer, int node, int fromEdge, List<Integer> chain, Set<Integer> seen) {
    int currentNode = node;
    int currentEdge = fromEdge;
    while (true) {
      EdgeIterator it = explorer.setBaseNode(currentNode);
      int degree = 0;
      int nextEdge = -1;
      int nextNode = -1;
      while (it.next()) {
        degree++;
        if (it.getEdge() != currentEdge) {
          nextEdge = it.getEdge();
          nextNode = it.getAdjNode();
        }
      }
      if (degree != 2 || nextEdge < 0 || seen.contains(nextEdge)) {
        break;
      }
      chain.add(nextEdge);
      seen.add(nextEdge);
      currentEdge = nextEdge;
      currentNode = nextNode;
    }
  }
}

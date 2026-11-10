package com.runmap.api.map.pipeline;

import com.graphhopper.GraphHopper;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
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
 * <p>교차점·막다른 노드(degree != 2)가 체인의 경계다. 통과 노드(degree == 2)로만 이어지는 edge들은 한 segment로 묶인다. 번호는 체인의 노드
 * 좌표에서 나오므로, 같은 OSM 스냅샷이면 edge 순회 순서나 서비스 지역 확장과 무관하게 항상 같은 값이다.
 */
public final class SegmentBuilder {

  /** 좌표 양자화 (1e-7deg ~ 1cm). */
  private static final double COORD_SCALE = 1e7;

  private SegmentBuilder() {}

  /** edgeId → segmentId 매핑. */
  public static long[] build(GraphHopper gh) {
    BaseGraph graph = gh.getBaseGraph();
    NodeAccess nodes = graph.getNodeAccess();
    int edges = graph.getEdges();
    long[] segmentIdByEdge = new long[edges];
    Arrays.fill(segmentIdByEdge, -1L);

    EdgeExplorer explorer = graph.createEdgeExplorer();
    for (int edge = 0; edge < edges; edge++) {
      if (segmentIdByEdge[edge] != -1L) {
        continue;
      }
      List<Integer> chain = chainOf(graph, explorer, edge);
      long id = chainId(graph, nodes, chain);
      for (int chainEdge : chain) {
        segmentIdByEdge[chainEdge] = id;
      }
    }
    return segmentIdByEdge;
  }

  /** 체인 지오메트리에서 나오는 결정론적 id. 끝 노드 순서·edge 순회 순서와 무관하게(교환 합) 같은 값이고, 좌표 기반이라 지역이 넓어져도 불변이다. */
  private static long chainId(BaseGraph graph, NodeAccess nodes, List<Integer> chain) {
    long acc = 0;
    for (int edge : chain) {
      EdgeIteratorState state = graph.getEdgeIteratorState(edge, Integer.MIN_VALUE);
      acc += coordHash(nodes, state.getBaseNode());
      acc += coordHash(nodes, state.getAdjNode());
    }
    return fmix64(acc) & Long.MAX_VALUE; // 음수 금지 (-1 = 정복 대상 아님)
  }

  private static long coordHash(NodeAccess nodes, int node) {
    long lat = Math.round(nodes.getLat(node) * COORD_SCALE);
    long lon = Math.round(nodes.getLon(node) * COORD_SCALE);
    return fmix64(lat * 1_000_003L ^ fmix64(lon));
  }

  /** SplitMix64 finalizer. */
  private static long fmix64(long z) {
    z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
    z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
    return z ^ (z >>> 31);
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

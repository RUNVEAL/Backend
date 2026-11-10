package com.runmap.api.map.pipeline;

import com.graphhopper.GraphHopper;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 그래프의 edge를 교차점 사이 체인으로 묶고, 나란한(보행 회랑) 체인을 병합해 결정론적 segment 번호를 만든다 (segments.bin 생성용, A 소유).
 *
 * <p>번호는 체인의 노드 좌표에서 나오므로 같은 OSM 스냅샷이면 edge 순회 순서·지역 확장과 무관하게 항상 같다.
 */
public final class SegmentBuilder {

  /** 좌표 양자화 (1e-7deg ~ 1cm). */
  private static final double COORD_SCALE = 1e7;

  /** 평행 병합: 대표와 이 거리(m) 이내면 같은 길(보행 회랑)로 본다. */
  private static final int MERGE_DIST_M = 15;

  private SegmentBuilder() {}

  private record Chain(List<Integer> edges, int endA, int endB) {}

  /** edgeId → segmentId 매핑. */
  public static long[] build(GraphHopper gh) {
    BaseGraph graph = gh.getBaseGraph();
    NodeAccess nodes = graph.getNodeAccess();
    int edges = graph.getEdges();

    // 1) 교차점 사이 체인 수집
    boolean[] visited = new boolean[edges];
    EdgeExplorer explorer = graph.createEdgeExplorer();
    List<Chain> chains = new ArrayList<>();
    for (int edge = 0; edge < edges; edge++) {
      if (visited[edge]) {
        continue;
      }
      Chain chain = chainOf(graph, explorer, edge);
      for (int chainEdge : chain.edges()) {
        visited[chainEdge] = true;
      }
      chains.add(chain);
    }

    // 2) 체인별 id · centroid · 교차점쌍
    long[] ids = new long[chains.size()];
    double[][] centroids = new double[chains.size()][];
    long[] pairKeys = new long[chains.size()];
    for (int i = 0; i < chains.size(); i++) {
      Chain chain = chains.get(i);
      ids[i] = chainId(graph, nodes, chain.edges());
      centroids[i] = centroid(graph, nodes, chain.edges());
      pairKeys[i] = pairKey(chain.endA(), chain.endB());
    }

    // 3) 나란한 체인 병합 (보행 회랑)
    long[] mergedIds = mergeParallels(pairKeys, ids, centroids);

    // 4) edge → segmentId
    long[] segmentIdByEdge = new long[edges];
    Arrays.fill(segmentIdByEdge, -1L);
    for (int i = 0; i < chains.size(); i++) {
      for (int chainEdge : chains.get(i).edges()) {
        segmentIdByEdge[chainEdge] = mergedIds[i];
      }
    }
    return segmentIdByEdge;
  }

  /**
   * 같은 교차점 쌍을 잇고 서로 가까운(평행·보행 회랑) 체인을 하나로 병합한다. 같은 쌍이고 대표(최소 id)와 {@value #MERGE_DIST_M}m 이내면 대표
   * id로 합친다. 멀면 다른 길로 보고 병합하지 않는다(보수적).
   */
  static long[] mergeParallels(long[] pairKeys, long[] ids, double[][] centroids) {
    long[] merged = ids.clone();
    Map<Long, List<Integer>> byPair = new HashMap<>();
    for (int i = 0; i < pairKeys.length; i++) {
      byPair.computeIfAbsent(pairKeys[i], key -> new ArrayList<>()).add(i);
    }
    for (List<Integer> group : byPair.values()) {
      if (group.size() < 2) {
        continue;
      }
      int rep = group.get(0);
      for (int idx : group) {
        if (ids[idx] < ids[rep]) {
          rep = idx;
        }
      }
      for (int idx : group) {
        if (idx != rep && distanceM(centroids[rep], centroids[idx]) <= MERGE_DIST_M) {
          merged[idx] = ids[rep];
        }
      }
    }
    return merged;
  }

  /** 두 [lat, lng] 사이 평면 근사 거리(m). */
  private static double distanceM(double[] a, double[] b) {
    double dLat = (a[0] - b[0]) * 111_320;
    double dLng = (a[1] - b[1]) * 111_320 * Math.cos(Math.toRadians((a[0] + b[0]) / 2));
    return Math.hypot(dLat, dLng);
  }

  /** 체인 지오메트리에서 나오는 결정론적 id. 끝 노드·edge 순서와 무관(교환 합)하고 좌표 기반이라 지역 확장에도 불변이다. */
  private static long chainId(BaseGraph graph, NodeAccess nodes, List<Integer> chain) {
    long acc = 0;
    for (int edge : chain) {
      EdgeIteratorState state = graph.getEdgeIteratorState(edge, Integer.MIN_VALUE);
      acc += coordHash(nodes, state.getBaseNode());
      acc += coordHash(nodes, state.getAdjNode());
    }
    return fmix64(acc) & Long.MAX_VALUE; // 음수 금지 (-1 = 정복 대상 아님)
  }

  /** 체인 노드 좌표의 평균 [lat, lng]. */
  private static double[] centroid(BaseGraph graph, NodeAccess nodes, List<Integer> chain) {
    double lat = 0;
    double lng = 0;
    for (int edge : chain) {
      EdgeIteratorState state = graph.getEdgeIteratorState(edge, Integer.MIN_VALUE);
      lat += nodes.getLat(state.getBaseNode()) + nodes.getLat(state.getAdjNode());
      lng += nodes.getLon(state.getBaseNode()) + nodes.getLon(state.getAdjNode());
    }
    int points = chain.size() * 2;
    return new double[] {lat / points, lng / points};
  }

  /** 끝 노드 쌍을 순서 무관 키로. */
  private static long pairKey(int a, int b) {
    return ((long) Math.min(a, b) << 32) | (Math.max(a, b) & 0xffffffffL);
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

  /** edge가 속한, 교차점 사이 체인. 끝 노드(degree != 2)도 함께 돌려준다. */
  private static Chain chainOf(BaseGraph graph, EdgeExplorer explorer, int startEdge) {
    List<Integer> edges = new ArrayList<>();
    Set<Integer> seen = new HashSet<>();
    edges.add(startEdge);
    seen.add(startEdge);

    EdgeIteratorState start = graph.getEdgeIteratorState(startEdge, Integer.MIN_VALUE);
    int endA = extend(explorer, start.getBaseNode(), startEdge, edges, seen);
    int endB = extend(explorer, start.getAdjNode(), startEdge, edges, seen);
    return new Chain(edges, endA, endB);
  }

  /** 통과 노드(degree == 2)로만 이어지는 동안 체인을 늘리고, 멈춘 노드(끝)를 돌려준다. */
  private static int extend(
      EdgeExplorer explorer, int node, int fromEdge, List<Integer> edges, Set<Integer> seen) {
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
      edges.add(nextEdge);
      seen.add(nextEdge);
      currentEdge = nextEdge;
      currentNode = nextNode;
    }
    return currentNode;
  }
}

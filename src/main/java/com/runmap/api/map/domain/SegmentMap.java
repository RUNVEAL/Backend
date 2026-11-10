package com.runmap.api.map.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * edgeId ↔ segmentId 양방향 조회. 파이프라인 segments.bin을 로드했으면 그 매핑을, 없으면 identity(segmentId = edgeId)를 쓴다.
 */
public final class SegmentMap {

  private final long[] segmentIdByEdge;
  private final Map<Long, List<Integer>> edgesBySegment;

  private SegmentMap(long[] segmentIdByEdge) {
    this.segmentIdByEdge = segmentIdByEdge;
    Map<Long, List<Integer>> reverse = new HashMap<>();
    for (int edge = 0; edge < segmentIdByEdge.length; edge++) {
      long segmentId = segmentIdByEdge[edge];
      if (segmentId >= 0) {
        reverse.computeIfAbsent(segmentId, k -> new ArrayList<>()).add(edge);
      }
    }
    this.edgesBySegment = reverse;
  }

  /** segments.bin 없을 때: segmentId = edgeId (단, 0 <= edgeId < edgeCount). */
  public static SegmentMap identity(int edgeCount) {
    long[] identity = new long[edgeCount];
    for (int edge = 0; edge < edgeCount; edge++) {
      identity[edge] = edge;
    }
    return new SegmentMap(identity);
  }

  /** segments.bin 로드: 인덱스 = edgeId, 값 = segmentId. */
  public static SegmentMap of(long[] segmentIdByEdge) {
    return new SegmentMap(segmentIdByEdge.clone());
  }

  /** 정복 대상이 아니면 -1. */
  public long segmentOf(int edgeId) {
    return (edgeId >= 0 && edgeId < segmentIdByEdge.length) ? segmentIdByEdge[edgeId] : -1L;
  }

  /** 모르는 segmentId는 무시. */
  public Set<Integer> edgesOf(Collection<Long> segmentIds) {
    Set<Integer> edges = new HashSet<>();
    for (Long segmentId : segmentIds) {
      List<Integer> ofSegment = edgesBySegment.get(segmentId);
      if (ofSegment != null) {
        edges.addAll(ofSegment);
      }
    }
    return edges;
  }
}

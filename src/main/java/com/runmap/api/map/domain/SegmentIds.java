package com.runmap.api.map.domain;

import com.graphhopper.GraphHopper;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * GraphHopper edgeId ↔ 구간(segment) ID.
 *
 * <p>주인: 우선님. 파이프라인의 segments.bin 이 나오면 내부만 교체한다. 시그니처는 계약. 지금은 identity (segmentId = edgeId).
 */
@Component
public class SegmentIds {

  private final int edgeCount;

  public SegmentIds(GraphHopper gh) {
    this.edgeCount = gh.getBaseGraph().getEdges();
  }

  /** 정복 대상이 아니면 -1. */
  public long segmentOf(int edgeId) {
    return edgeId;
  }

  /** 모르는 ID(음수·범위 밖)는 무시한다 (API 명세 §3.2). */
  public Set<Integer> edgesOf(Collection<Long> segmentIds) {
    return segmentIds.stream()
        .filter(id -> id >= 0 && id < edgeCount)
        .map(Long::intValue)
        .collect(Collectors.toSet());
  }
}

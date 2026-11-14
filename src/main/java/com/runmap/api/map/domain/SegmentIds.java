package com.runmap.api.map.domain;

import com.graphhopper.GraphHopper;
import com.runmap.api.map.config.MapProperties;
import com.runmap.api.map.pipeline.SegmentsFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * GraphHopper edgeId ↔ 구간(segment) ID.
 *
 * <p>주인: 우선님. 파이프라인 {@code segments.bin}(그래프 디렉터리)이 있으면 그 매핑을, 없으면 identity(segmentId = edgeId)를
 * 쓴다. 시그니처는 계약.
 */
@Component
public class SegmentIds {

  private final SegmentMap map;

  public SegmentIds(GraphHopper gh, MapProperties props) {
    Path file = Path.of(props.graphLocation(), "segments.bin");
    if (Files.exists(file)) {
      try {
        this.map = SegmentMap.of(SegmentsFile.read(file));
      } catch (IOException e) {
        throw new IllegalStateException("failed to load " + file, e);
      }
    } else {
      this.map = SegmentMap.identity(gh.getBaseGraph().getEdges());
    }
  }

  /** 정복 대상이 아니면 -1. */
  public long segmentOf(int edgeId) {
    return map.segmentOf(edgeId);
  }

  /** 한 segment에 속한 edge들 (없으면 빈 목록). */
  public List<Integer> edgesOf(long segmentId) {
    return map.edgesOf(segmentId);
  }

  /** 모르는 ID는 무시한다 (API 명세 §3.2). */
  public Set<Integer> edgesOf(Collection<Long> segmentIds) {
    return map.edgesOf(segmentIds);
  }
}

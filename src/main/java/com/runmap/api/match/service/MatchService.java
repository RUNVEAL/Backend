package com.runmap.api.match.service;

import com.graphhopper.GraphHopper;
import com.graphhopper.matching.EdgeMatch;
import com.graphhopper.matching.MapMatching;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.matching.Observation;
import com.graphhopper.util.PMap;
import com.graphhopper.util.shapes.GHPoint;
import com.runmap.api.map.domain.MapGraphFactory;
import com.runmap.api.map.domain.SegmentIds;
import com.runmap.api.match.dto.MatchResponse;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 맵매칭 엔진. GPS 트랙을 그래프에 스냅해 달린 것으로 인정된 구간을 찾는다.
 *
 * <p>주인: 우선님. 입력은 이미 검증됨(10~2000점, [lng, lat], 범위 안). 엔진 실패는 {@code BusinessException(MATCH_FAILED
 * | OUT_OF_SERVICE_AREA)}로 던지고, 예외 메시지에 좌표를 넣지 않는다.
 */
@Service
public class MatchService {

  private final GraphHopper gh;
  private final SegmentIds segmentIds;

  public MatchService(GraphHopper gh, SegmentIds segmentIds) {
    this.gh = gh;
    this.segmentIds = segmentIds;
  }

  public MatchResponse match(String mapVersion, List<double[]> points) {
    MapMatching mapMatching =
        MapMatching.fromGraphHopper(gh, new PMap().putObject("profile", MapGraphFactory.PROFILE));

    List<Observation> observations = new ArrayList<>(points.size());
    for (double[] p : points) {
      observations.add(new Observation(new GHPoint(p[1], p[0]))); // p = [lng, lat]
    }
    MatchResult result = mapMatching.match(observations);

    LinkedHashSet<Long> segments = new LinkedHashSet<>();
    for (EdgeMatch em : result.getEdgeMatches()) {
      long segment = segmentIds.segmentOf(em.getEdgeState().getEdge());
      if (segment >= 0) {
        segments.add(segment);
      }
    }

    int matchedDistanceM = (int) Math.round(result.getMatchLength());
    return new MatchResponse(
        mapVersion, List.copyOf(segments), List.of(), matchedDistanceM, List.of());
  }
}

package com.runmap.api.match.service;

import com.graphhopper.GraphHopper;
import com.graphhopper.matching.EdgeMatch;
import com.graphhopper.matching.MapMatching;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.matching.Observation;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.PMap;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;
import com.runmap.api.global.error.BusinessException;
import com.runmap.api.global.error.ErrorCode;
import com.runmap.api.map.domain.AdminAreas;
import com.runmap.api.map.domain.MapGraphFactory;
import com.runmap.api.map.domain.SegmentIds;
import com.runmap.api.map.domain.Segments;
import com.runmap.api.match.dto.MatchResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/**
 * 맵매칭 엔진. GPS 트랙을 그래프에 스냅해 달린 것으로 인정된 구간을 찾는다.
 *
 * <p>주인: 우선님. 입력은 이미 검증됨(10~2000점, [lng, lat], 범위 안). 서비스 지역 밖 점은 건너뛰고 범위 안 점들의 연속 구간(run)만 매칭한다.
 * 매칭 가능한 구간이 없으면 {@code OUT_OF_SERVICE_AREA}. 예외 메시지에 좌표를 넣지 않는다.
 */
@Service
public class MatchService {

  /** 맵매칭에 필요한 최소 관측점 수. */
  private static final int MIN_RUN_POINTS = 2;

  /** 인정 최소 커버리지 — 매칭 길이 ÷ 구간 전체 길이. manifest 의 segmentCoverage 와 같아야 한다. */
  private static final double MIN_SEGMENT_COVERAGE = 0.6;

  /** 이 거리(m) 이상 벌어진 GPS 간격에서는 run 을 끊는다 — 큰 공백을 한 번에 매칭하려다 MATCH_FAILED 나는 걸 방지. */
  private static final double MAX_GAP_M = 500;

  private final GraphHopper gh;
  private final SegmentIds segmentIds;
  private final AdminAreas adminAreas;

  public MatchService(GraphHopper gh, SegmentIds segmentIds, AdminAreas adminAreas) {
    this.gh = gh;
    this.segmentIds = segmentIds;
    this.adminAreas = adminAreas;
  }

  public MatchResponse match(String mapVersion, List<double[]> points) {
    MapMatching mapMatching =
        MapMatching.fromGraphHopper(gh, new PMap().putObject("profile", MapGraphFactory.PROFILE));

    List<List<double[]>> runs = inAreaRuns(points, mapMatching);
    if (runs.isEmpty()) {
      throw new BusinessException(ErrorCode.OUT_OF_SERVICE_AREA);
    }

    BaseGraph graph = gh.getBaseGraph();
    NodeAccess nodes = graph.getNodeAccess();
    Map<Long, Set<Integer>> matchedEdgesBySegment = new LinkedHashMap<>();
    List<double[]> matchedPath = new ArrayList<>();
    double matchedLengthM = 0;
    for (List<double[]> run : runs) {
      MatchResult result;
      try {
        result = matchRun(mapMatching, toObservations(run));
      } catch (RuntimeException e) {
        // 좌표 무로그: GraphHopper 예외 메시지엔 좌표가 들어가므로 원본을 넘기지 않는다
        throw new BusinessException(ErrorCode.MATCH_FAILED);
      }
      for (EdgeMatch em : result.getEdgeMatches()) {
        int edgeId = em.getEdgeState().getEdge();
        long segment = segmentIds.segmentOf(edgeId);
        if (segment >= 0) {
          matchedEdgesBySegment.computeIfAbsent(segment, key -> new LinkedHashSet<>()).add(edgeId);
        }
      }
      appendPoints(matchedPath, result.getMergedPath().calcPoints());
      matchedLengthM += result.getMatchLength();
    }

    // 인정 규칙(P1-1): 매칭 edge 길이 ÷ 구간 전체 길이 ≥ MIN_SEGMENT_COVERAGE 인 구간만 '달린 것'으로
    // 인정한다. 스친 구간을 통째로 인정해 정복률이 부풀려지는 것을 막는다.
    LinkedHashSet<Long> segments =
        acceptedSegments(graph, segmentIds::edgesOf, matchedEdgesBySegment);

    // 인정 구간 → 동·구: 분모(RegionTotals)와 같은 규칙(구간 전체 centroid)으로 배정한다.
    LinkedHashSet<String> sigungu = new LinkedHashSet<>();
    LinkedHashMap<Long, String> dongBySegment = new LinkedHashMap<>();
    for (long segment : segments) {
      double[] center = Segments.centroid(graph, nodes, segmentIds.edgesOf(segment));
      adminAreas.codeAt(center[0], center[1]).ifPresent(code -> dongBySegment.put(segment, code));
      adminAreas.guAt(center[0], center[1]).ifPresent(sigungu::add);
    }

    return new MatchResponse(
        mapVersion,
        List.copyOf(segments),
        List.copyOf(matchedPath),
        (int) Math.round(matchedLengthM),
        List.copyOf(sigungu),
        Map.copyOf(dongBySegment));
  }

  /** 엔진 호출을 분리 — 테스트에서 실패를 주입할 수 있게 한다. */
  protected MatchResult matchRun(MapMatching mapMatching, List<Observation> observations) {
    return mapMatching.match(observations);
  }

  /** 매칭된 edge 길이가 구간 전체의 {@value #MIN_SEGMENT_COVERAGE} 이상을 덮는 구간만 인정한다. */
  public static LinkedHashSet<Long> acceptedSegments(
      BaseGraph graph,
      Function<Long, List<Integer>> edgesOfSegment,
      Map<Long, Set<Integer>> matchedEdgesBySegment) {
    LinkedHashSet<Long> accepted = new LinkedHashSet<>();
    matchedEdgesBySegment.forEach(
        (segment, matchedEdges) -> {
          double total = edgeLength(graph, edgesOfSegment.apply(segment));
          if (total > 0 && edgeLength(graph, matchedEdges) / total >= MIN_SEGMENT_COVERAGE) {
            accepted.add(segment);
          }
        });
    return accepted;
  }

  private static double edgeLength(BaseGraph graph, Collection<Integer> edges) {
    double sum = 0;
    for (int edge : edges) {
      sum += graph.getEdgeIteratorState(edge, Integer.MIN_VALUE).getDistance();
    }
    return sum;
  }

  /** 서비스 지역 안 점들의 연속 구간으로 나눈다. 지역 밖·큰 GPS 간격에서 끊고, 최소 점 수 미만은 버린다. */
  private List<List<double[]>> inAreaRuns(List<double[]> points, MapMatching mapMatching) {
    List<List<double[]>> runs = new ArrayList<>();
    List<double[]> current = new ArrayList<>();
    for (double[] p : points) {
      if (!mapMatching.findCandidateSnaps(p[1], p[0]).isEmpty()) {
        current.add(p);
      } else {
        addRuns(runs, current);
        current = new ArrayList<>();
      }
    }
    addRuns(runs, current);
    return runs;
  }

  /** 지역 안 점들을 큰 간격에서 쪼갠 뒤, 최소 점 수를 넘는 run 만 더한다. */
  private static void addRuns(List<List<double[]>> runs, List<double[]> inAreaPoints) {
    for (List<double[]> run : splitOnGaps(inAreaPoints)) {
      if (run.size() >= MIN_RUN_POINTS) {
        runs.add(run);
      }
    }
  }

  /** 연속 점 사이가 {@value #MAX_GAP_M}m 를 넘으면 거기서 run 을 끊는다. */
  public static List<List<double[]>> splitOnGaps(List<double[]> points) {
    List<List<double[]>> runs = new ArrayList<>();
    List<double[]> current = new ArrayList<>();
    double[] prev = null;
    for (double[] p : points) {
      if (prev != null && distanceM(prev, p) > MAX_GAP_M) {
        runs.add(current);
        current = new ArrayList<>();
      }
      current.add(p);
      prev = p;
    }
    if (!current.isEmpty()) {
      runs.add(current);
    }
    return runs;
  }

  /** 두 점 [lng, lat] 사이 평면 근사 거리(m). */
  private static double distanceM(double[] a, double[] b) {
    double dLat = (b[1] - a[1]) * 111_320;
    double dLng = (b[0] - a[0]) * 111_320 * Math.cos(Math.toRadians((a[1] + b[1]) / 2));
    return Math.hypot(dLat, dLng);
  }

  private static List<Observation> toObservations(List<double[]> run) {
    List<Observation> observations = new ArrayList<>(run.size());
    for (double[] p : run) {
      observations.add(new Observation(new GHPoint(p[1], p[0]))); // p = [lng, lat]
    }
    return observations;
  }

  private static void appendPoints(List<double[]> matchedPath, PointList points) {
    for (int i = 0; i < points.size(); i++) {
      matchedPath.add(new double[] {points.getLon(i), points.getLat(i)});
    }
  }
}

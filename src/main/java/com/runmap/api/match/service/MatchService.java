package com.runmap.api.match.service;

import com.graphhopper.GraphHopper;
import com.graphhopper.matching.EdgeMatch;
import com.graphhopper.matching.MapMatching;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.matching.Observation;
import com.graphhopper.util.PMap;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;
import com.runmap.api.global.error.BusinessException;
import com.runmap.api.global.error.ErrorCode;
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
 * <p>주인: 우선님. 입력은 이미 검증됨(10~2000점, [lng, lat], 범위 안). 서비스 지역 밖 점은 건너뛰고 범위 안 점들의 연속 구간(run)만 매칭한다.
 * 매칭 가능한 구간이 없으면 {@code OUT_OF_SERVICE_AREA}. 예외 메시지에 좌표를 넣지 않는다.
 */
@Service
public class MatchService {

  /** 맵매칭에 필요한 최소 관측점 수. */
  private static final int MIN_RUN_POINTS = 2;

  private final GraphHopper gh;
  private final SegmentIds segmentIds;

  public MatchService(GraphHopper gh, SegmentIds segmentIds) {
    this.gh = gh;
    this.segmentIds = segmentIds;
  }

  public MatchResponse match(String mapVersion, List<double[]> points) {
    MapMatching mapMatching =
        MapMatching.fromGraphHopper(gh, new PMap().putObject("profile", MapGraphFactory.PROFILE));

    List<List<double[]>> runs = inAreaRuns(points, mapMatching);
    if (runs.isEmpty()) {
      throw new BusinessException(ErrorCode.OUT_OF_SERVICE_AREA);
    }

    LinkedHashSet<Long> segments = new LinkedHashSet<>();
    List<double[]> matchedPath = new ArrayList<>();
    double matchedLengthM = 0;
    for (List<double[]> run : runs) {
      MatchResult result = mapMatching.match(toObservations(run));
      for (EdgeMatch em : result.getEdgeMatches()) {
        long segment = segmentIds.segmentOf(em.getEdgeState().getEdge());
        if (segment >= 0) {
          segments.add(segment);
        }
      }
      appendPoints(matchedPath, result.getMergedPath().calcPoints());
      matchedLengthM += result.getMatchLength();
    }

    return new MatchResponse(
        mapVersion,
        List.copyOf(segments),
        List.copyOf(matchedPath),
        (int) Math.round(matchedLengthM),
        List.of());
  }

  /** 서비스 지역 안 점들의 연속 구간으로 나눈다. 매칭에 필요한 최소 점 수에 못 미치는 구간은 버린다. */
  private List<List<double[]>> inAreaRuns(List<double[]> points, MapMatching mapMatching) {
    List<List<double[]>> runs = new ArrayList<>();
    List<double[]> current = new ArrayList<>();
    for (double[] p : points) {
      if (!mapMatching.findCandidateSnaps(p[1], p[0]).isEmpty()) {
        current.add(p);
      } else {
        addRun(runs, current);
        current = new ArrayList<>();
      }
    }
    addRun(runs, current);
    return runs;
  }

  private static void addRun(List<List<double[]>> runs, List<double[]> run) {
    if (run.size() >= MIN_RUN_POINTS) {
      runs.add(run);
    }
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

package com.runmap.api.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.graphhopper.GHRequest;
import com.graphhopper.GraphHopper;
import com.graphhopper.matching.MapMatching;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.matching.Observation;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.PointList;
import com.runmap.api.global.error.BusinessException;
import com.runmap.api.global.error.ErrorCode;
import com.runmap.api.map.domain.AdminAreas;
import com.runmap.api.map.domain.MapGraphFactory;
import com.runmap.api.map.domain.SegmentIds;
import com.runmap.api.map.pipeline.SegmentBuilder;
import com.runmap.api.match.dto.MatchResponse;
import com.runmap.api.match.service.MatchService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MatchServiceTest {

  @Autowired GraphHopper gh;
  @Autowired MatchService matchService;
  @Autowired SegmentIds segmentIds;
  @Autowired AdminAreas adminAreas;

  /** 서교동 fixture 도로를 실제로 따라가는 트랙을 라우팅으로 만든다 (= 노이즈 없는 on-road GPS). */
  private List<double[]> onRoadTrack() {
    PointList pts =
        gh.route(
                new GHRequest(37.5552, 126.9236, 37.5552, 126.9326)
                    .setProfile(MapGraphFactory.PROFILE))
            .getBest()
            .getPoints();
    List<double[]> track = new ArrayList<>();
    for (int i = 0; i < pts.size(); i++) {
      track.add(new double[] {pts.getLon(i), pts.getLat(i)});
    }
    return track;
  }

  @Test
  void matchesOnRoadTrackToSegments() {
    MatchResponse res = matchService.match("2026-10-27", onRoadTrack());

    assertThat(res.segmentIds()).isNotEmpty();
    assertThat(res.matchedDistanceM()).isGreaterThan(0);
  }

  @Test
  void allPointsOutOfServiceAreaThrows() {
    // 서교동 fixture에서 한참 먼 좌표 (동중국해 부근)
    List<double[]> offMap =
        List.of(
            new double[] {120.0, 30.0}, new double[] {120.001, 30.0}, new double[] {120.002, 30.0});

    assertThatThrownBy(() -> matchService.match("2026-10-27", offMap))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OUT_OF_SERVICE_AREA));
  }

  @Test
  void matchesInAreaRunsWhenSomePointsAreOutside() {
    List<double[]> mixed = new ArrayList<>();
    mixed.add(new double[] {120.0, 30.0}); // 범위 밖
    mixed.add(new double[] {120.001, 30.0}); // 범위 밖
    mixed.addAll(onRoadTrack()); // 범위 안 run

    MatchResponse res = matchService.match("2026-10-27", mixed);

    assertThat(res.segmentIds()).isNotEmpty();
    assertThat(res.matchedDistanceM()).isGreaterThan(0);
  }

  @Test
  void returnsSnappedPathForOnRoadTrack() {
    MatchResponse res = matchService.match("2026-10-27", onRoadTrack());

    assertThat(res.matchedPath()).isNotEmpty();
    assertThat(res.matchedPath().get(0)).hasSize(2); // [lng, lat]
  }

  @Test
  void acceptsSegmentOnlyWhenCoverageMeetsThreshold() {
    long[] segByEdge = SegmentBuilder.build(gh);
    BaseGraph graph = gh.getBaseGraph();

    // edge가 2개 이상인 구간 하나 (짧은 edge 하나는 ≤50% < 60%)
    Map<Long, List<Integer>> bySegment = new LinkedHashMap<>();
    for (int e = 0; e < segByEdge.length; e++) {
      if (segByEdge[e] >= 0) {
        bySegment.computeIfAbsent(segByEdge[e], k -> new ArrayList<>()).add(e);
      }
    }
    Map.Entry<Long, List<Integer>> multi =
        bySegment.entrySet().stream()
            .filter(x -> x.getValue().size() >= 2)
            .findFirst()
            .orElseThrow();
    long seg = multi.getKey();
    List<Integer> edges = multi.getValue();

    // 전체 edge 매칭(커버리지 100%) → 인정
    Map<Long, Set<Integer>> full = Map.of(seg, new LinkedHashSet<>(edges));
    assertThat(MatchService.acceptedSegments(graph, bySegment::get, full)).contains(seg);

    // 가장 짧은 edge 하나만 매칭(<60%) → 제외
    int shortest =
        edges.stream()
            .min(
                Comparator.comparingDouble(
                    e -> graph.getEdgeIteratorState(e, Integer.MIN_VALUE).getDistance()))
            .orElseThrow();
    Map<Long, Set<Integer>> partial = Map.of(seg, new LinkedHashSet<>(Set.of(shortest)));
    assertThat(MatchService.acceptedSegments(graph, bySegment::get, partial)).doesNotContain(seg);
  }

  @Test
  void splitsRunAtLargeGpsGap() {
    // 앞 두 점은 ~9m, 세 번째는 ~560m 점프 → 500m 초과 간격에서 끊겨 2개 run
    List<double[]> points =
        List.of(
            new double[] {126.9236, 37.5552},
            new double[] {126.9237, 37.5552},
            new double[] {126.9300, 37.5552},
            new double[] {126.9301, 37.5552});

    List<List<double[]>> runs = MatchService.splitOnGaps(points);

    assertThat(runs).hasSize(2);
    assertThat(runs.get(0)).hasSize(2);
    assertThat(runs.get(1)).hasSize(2);
  }

  @Test
  void returnsSigunguCodesForMatchedTrack() {
    MatchResponse res = matchService.match("2026-10-27", onRoadTrack());

    assertThat(res.sigunguCodes()).contains("G1"); // 서교동 → 합성 경계상 구 G1
  }

  @Test
  void returnsDongCodePerMatchedSegment() {
    MatchResponse res = matchService.match("2026-10-27", onRoadTrack());

    assertThat(res.dongBySegment()).isNotEmpty();
    assertThat(res.dongBySegment().values()).contains("B"); // 서교동 트랙 → 합성 경계상 동 B
    // 키(동이 매겨진 구간)는 인정된 구간 집합의 부분집합
    assertThat(res.segmentIds()).containsAll(res.dongBySegment().keySet());
  }

  @Test
  void translatesEngineFailureToMatchFailedWithoutLeakingCoords() {
    MatchService failing =
        new MatchService(gh, segmentIds, adminAreas) {
          @Override
          protected MatchResult matchRun(MapMatching mapMatching, List<Observation> observations) {
            throw new IllegalArgumentException("Sequence broken at 37.5552,126.9236"); // 좌표 포함
          }
        };

    assertThatThrownBy(() -> failing.match("2026-10-27", onRoadTrack()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MATCH_FAILED))
        .satisfies(e -> assertThat(e.getMessage()).doesNotContain("126.9236")); // 좌표 무로그
  }
}

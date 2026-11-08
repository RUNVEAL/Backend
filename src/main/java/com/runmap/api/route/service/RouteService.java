package com.runmap.api.route.service;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.exceptions.PointOutOfBoundsException;
import com.graphhopper.util.shapes.GHPoint;
import com.runmap.api.global.error.BusinessException;
import com.runmap.api.global.error.ErrorCode;
import com.runmap.api.map.domain.MapGraphFactory;
import com.runmap.api.route.dto.RouteRecommendRequest;
import com.runmap.api.route.dto.RouteRecommendResponse;
import com.runmap.api.route.dto.RouteRecommendResponse.RouteDto;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** 코스 후보를 여러 개 만들어 목표 거리 허용 범위 안의 것을 최대 3개 고른다 (API 명세 §3.2). 상태 없음. */
@Service
public class RouteService {

  static final int CANDIDATES = 5; // PoC best-of-k
  static final int MAX_ROUTES = 3;
  static final double LOOP_TOLERANCE = 0.15;
  // ponytail: GH round_trip 은 짧게 나온다. fixture 실측 보정값(2·3km 10/10), 서울 그래프로 eval 후 조정
  static final double LOOP_DISTANCE_FACTOR = 1.1;

  private final GraphHopper gh;
  private final RouteAssembler assembler;

  public RouteService(GraphHopper gh, RouteAssembler assembler) {
    this.gh = gh;
    this.assembler = assembler;
  }

  public RouteRecommendResponse recommend(RouteRecommendRequest req) {
    GHPoint start = round4(req.start());
    int target = req.distanceM();
    long seed0 = seedOf(req, start);
    List<RouteDto> candidates = new ArrayList<>();
    for (int i = 0; i < CANDIDATES; i++) {
      GHRequest r =
          new GHRequest(List.of(start))
              .setProfile(MapGraphFactory.PROFILE)
              .setAlgorithm(Parameters.Algorithms.ROUND_TRIP)
              .setPathDetails(List.of(RouteAssembler.EDGE_ID));
      r.putHint(Parameters.Algorithms.RoundTrip.DISTANCE, target * LOOP_DISTANCE_FACTOR);
      r.putHint(Parameters.Algorithms.RoundTrip.SEED, seed0 + i);
      route(r)
          .filter(p -> Math.abs(p.getDistance() - target) <= target * LOOP_TOLERANCE)
          .ifPresent(p -> candidates.add(assembler.toDto(p, "LOOP", req)));
    }
    return pick(req.mapVersion(), candidates, target);
  }

  // 지역 밖이면 422, 그 밖의 엔진 실패(연결 없음 등)는 후보 하나를 버린다
  private Optional<ResponsePath> route(GHRequest r) {
    GHResponse res = gh.route(r);
    for (Throwable t : res.getErrors()) {
      if (t instanceof PointOutOfBoundsException || t instanceof PointNotFoundException) {
        throw new BusinessException(ErrorCode.OUT_OF_SERVICE_AREA);
      }
    }
    return res.hasErrors() ? Optional.empty() : Optional.of(res.getBest());
  }

  private static RouteRecommendResponse pick(String mapVersion, List<RouteDto> all, int target) {
    Map<String, RouteDto> distinct = new LinkedHashMap<>();
    all.forEach(r -> distinct.putIfAbsent(r.routeId(), r));
    List<RouteDto> best =
        distinct.values().stream()
            .sorted(Comparator.comparingInt(r -> Math.abs(r.distanceM() - target)))
            .limit(MAX_ROUTES)
            .toList();
    List<String> warnings =
        best.isEmpty() ? List.of(RouteRecommendResponse.NO_ROUTE_FOUND) : List.of();
    return new RouteRecommendResponse(mapVersion, best, warnings, null);
  }

  // 같은 입력 → 같은 시드 (API 명세 §2.6). enum hashCode 는 JVM마다 달라 이름을 쓴다
  private static long seedOf(RouteRecommendRequest req, GHPoint start) {
    return Objects.hash(
        start.lat,
        start.lon,
        req.end(),
        req.distanceM(),
        req.filters().stream().map(Enum::name).toList(),
        req.preferUnexplored());
  }

  static GHPoint round4(RouteRecommendRequest.Point p) {
    return new GHPoint(Math.round(p.lat() * 1e4) / 1e4, Math.round(p.lng() * 1e4) / 1e4);
  }
}

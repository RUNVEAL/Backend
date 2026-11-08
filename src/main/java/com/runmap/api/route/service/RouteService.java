package com.runmap.api.route.service;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.json.Statement;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.shapes.GHPoint;
import com.runmap.api.global.error.BusinessException;
import com.runmap.api.global.error.ErrorCode;
import com.runmap.api.map.domain.AreaEncodedValues;
import com.runmap.api.map.domain.MapGraphFactory;
import com.runmap.api.map.domain.SegmentIds;
import com.runmap.api.route.domain.OneWayVias;
import com.runmap.api.route.domain.RouteFilter;
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
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/** 코스 후보를 여러 개 만들어 목표 거리 허용 범위 안의 것을 최대 3개 고른다 (API 명세 §3.2). 상태 없음. */
@Service
public class RouteService {

  static final int CANDIDATES = 5; // PoC best-of-k
  public static final int MAX_ROUTES = 3;
  public static final double LOOP_TOLERANCE = 0.15;
  public static final double ONE_WAY_TOLERANCE = 0.10;
  // ponytail: GH round_trip 은 짧게 나온다. fixture 실측 보정값(2·3km 10/10), 서울 그래프로 eval 후 조정
  static final double LOOP_DISTANCE_FACTOR = 1.1;

  private final GraphHopper gh;
  private final RouteAssembler assembler;
  private final SegmentIds segmentIds;

  public RouteService(GraphHopper gh, RouteAssembler assembler, SegmentIds segmentIds) {
    this.gh = gh;
    this.assembler = assembler;
    this.segmentIds = segmentIds;
  }

  public RouteRecommendResponse recommend(RouteRecommendRequest req) {
    GHPoint start = round4(req.start());
    Set<Integer> penalized =
        req.preferUnexplored() && req.exploredSegmentIds() != null
            ? segmentIds.edgesOf(req.exploredSegmentIds())
            : Set.of();
    CustomModel model = filterModel(req.filters());
    Function<List<GHPoint>, GHRequest> newRequest = points -> request(points, penalized, model);
    return req.end() == null
        ? loop(req, start, newRequest)
        : oneWay(req, start, round4(req.end()), newRequest);
  }

  // 필터 = 선호 가중치. LM 제약으로 곱은 1 이하만 → "공원 선호" = "공원 아닌 길 ×0.5"
  static CustomModel filterModel(List<RouteFilter> filters) {
    if (filters.isEmpty()) {
      return null;
    }
    CustomModel m = new CustomModel();
    if (filters.contains(RouteFilter.PARK)) {
      m.addToPriority(
          Statement.If("!" + AreaEncodedValues.NEAR_PARK, Statement.Op.MULTIPLY, "0.5"));
    }
    if (filters.contains(RouteFilter.RIVERSIDE)) {
      m.addToPriority(
          Statement.If("!" + AreaEncodedValues.NEAR_RIVER, Statement.Op.MULTIPLY, "0.5"));
    }
    if (filters.contains(RouteFilter.GENTLE_SLOPE)) {
      m.addToPriority(
          Statement.If("average_slope > 6 || average_slope < -6", Statement.Op.MULTIPLY, "0.3"));
    }
    return m;
  }

  private RouteRecommendResponse loop(
      RouteRecommendRequest req, GHPoint start, Function<List<GHPoint>, GHRequest> newRequest) {
    int target = req.distanceM();
    long seed0 = seedOf(req, start);
    List<RouteDto> candidates = new ArrayList<>();
    for (int i = 0; i < CANDIDATES; i++) {
      GHRequest r = newRequest.apply(List.of(start)).setAlgorithm(Parameters.Algorithms.ROUND_TRIP);
      r.putHint(Parameters.Algorithms.RoundTrip.DISTANCE, target * LOOP_DISTANCE_FACTOR);
      r.putHint(Parameters.Algorithms.RoundTrip.SEED, seed0 + i);
      route(r)
          .filter(p -> within(p, target, LOOP_TOLERANCE))
          .ifPresent(p -> candidates.add(assembler.toDto(p, "LOOP", req)));
    }
    return pick(req.mapVersion(), candidates, target);
  }

  // 최단이 목표보다 길면 B1-1 안내(D8). 아니면 최단 + 타원 경유지 8개 우회 중 ±10% 이내
  private RouteRecommendResponse oneWay(
      RouteRecommendRequest req,
      GHPoint start,
      GHPoint end,
      Function<List<GHPoint>, GHRequest> newRequest) {
    int target = req.distanceM();
    // 최단 = 선호 가중치(탐험 벌점·필터) 없는 진짜 최단 (명세 §3.2 규칙 3)
    Optional<ResponsePath> plain = route(request(List.of(start, end), Set.of(), null));
    if (plain.isEmpty()) {
      return pick(req.mapVersion(), List.of(), target);
    }
    ResponsePath shortest = plain.get();
    if (shortest.getDistance() > target) {
      return new RouteRecommendResponse(
          req.mapVersion(),
          List.of(),
          List.of(RouteRecommendResponse.DESTINATION_FARTHER),
          (int) Math.round(shortest.getDistance()));
    }
    List<ResponsePath> paths = new ArrayList<>();
    paths.add(shortest);
    for (GHPoint via : OneWayVias.of(start, end, target)) {
      GHRequest r = newRequest.apply(List.of(start, via, end));
      r.putHint(Parameters.Routing.PASS_THROUGH, true);
      route(r).ifPresent(paths::add);
    }
    List<RouteDto> candidates =
        paths.stream()
            .filter(p -> within(p, target, ONE_WAY_TOLERANCE))
            .map(p -> assembler.toDto(p, "ONE_WAY", req))
            .toList();
    return pick(req.mapVersion(), candidates, target);
  }

  private static GHRequest request(
      List<GHPoint> points, Set<Integer> penalized, CustomModel model) {
    GHRequest r =
        new GHRequest(points)
            .setProfile(MapGraphFactory.PROFILE)
            .setPathDetails(RouteAssembler.PATH_DETAILS);
    if (!penalized.isEmpty()) {
      r.getHints().putObject(MapGraphFactory.EXPLORED_EDGES_HINT, penalized);
    }
    if (model != null) {
      r.setCustomModel(model);
    }
    return r;
  }

  private static boolean within(ResponsePath p, int target, double tolerance) {
    return Math.abs(p.getDistance() - target) <= target * tolerance;
  }

  // 출발·도착이 지역 밖이면 422. 생성한 경유지가 밖이거나 엔진이 실패하면 후보 하나만 버린다
  private Optional<ResponsePath> route(GHRequest r) {
    GHResponse res = gh.route(r);
    int last = r.getPoints().size() - 1;
    for (Throwable t : res.getErrors()) {
      if (t instanceof PointNotFoundException e
          && (e.getPointIndex() == 0 || e.getPointIndex() == last)) {
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

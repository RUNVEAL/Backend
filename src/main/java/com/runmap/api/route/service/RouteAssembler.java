package com.runmap.api.route.service;

import com.graphhopper.ResponsePath;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.PointList;
import com.graphhopper.util.details.PathDetail;
import com.runmap.api.map.domain.AreaEncodedValues;
import com.runmap.api.map.domain.AreaNames;
import com.runmap.api.map.domain.SegmentIds;
import com.runmap.api.route.domain.ElevationProfile;
import com.runmap.api.route.domain.MilestoneKind;
import com.runmap.api.route.domain.Milestones;
import com.runmap.api.route.domain.Milestones.Piece;
import com.runmap.api.route.domain.RouteTags;
import com.runmap.api.route.dto.RouteRecommendRequest;
import com.runmap.api.route.dto.RouteRecommendResponse.MilestoneDto;
import com.runmap.api.route.dto.RouteRecommendResponse.RouteDto;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** GraphHopper 경로 하나 → 응답 코스 하나. */
@Component
public class RouteAssembler {

  static final String EDGE_ID = "edge_id";
  static final String STREET_NAME = "street_name";
  static final List<String> PATH_DETAILS =
      List.of(EDGE_ID, STREET_NAME, AreaEncodedValues.NEAR_PARK, AreaEncodedValues.NEAR_RIVER);
  static final int ELEVATION_STEP_M = 100; // D10

  private static final DistanceCalc DIST = DistanceCalcEarth.DIST_EARTH;

  private final SegmentIds segmentIds;
  private final AreaNames areaNames;

  public RouteAssembler(SegmentIds segmentIds, AreaNames areaNames) {
    this.segmentIds = segmentIds;
    this.areaNames = areaNames;
  }

  public RouteDto toDto(ResponsePath p, String type, RouteRecommendRequest req) {
    PointList pl = p.getPoints();
    List<double[]> path = lngLat(pl);
    int distanceM = (int) Math.round(p.getDistance());
    Map<String, List<PathDetail>> details = p.getPathDetails();
    List<PathDetail> edges = details.get(EDGE_ID);
    Set<Long> explored =
        req.exploredSegmentIds() == null ? null : new HashSet<>(req.exploredSegmentIds());

    Integer newRoadM = explored == null ? null : newRoadM(pl, edges, explored);
    Double newRoadRatio =
        newRoadM == null || distanceM == 0
            ? null
            : Math.min(1.0, Math.round(newRoadM * 100.0 / distanceM) / 100.0);

    List<int[]> profile = ElevationProfile.sample(pl, ELEVATION_STEP_M);
    if (profile != null) {
      profile.get(profile.size() - 1)[0] = distanceM; // 끝 점 = distanceM (명세 §3.2)
    }
    Integer gain = profile == null ? null : (int) Math.round(p.getAscend());

    Segments seg = segments(pl, details, explored);
    List<String> tags =
        RouteTags.of(distanceM, gain, profile, seg.parkM(), seg.riverM(), req.filters());
    List<MilestoneDto> milestones =
        Milestones.build(seg.pieces()).stream()
            .map(m -> new MilestoneDto(m.name(), m.kind().name(), m.startM(), m.endM()))
            .toList();

    String routeId = String.format("r_%08x", Arrays.deepHashCode(path.toArray(new double[0][])));
    return new RouteDto(
        routeId,
        type,
        distanceM,
        distanceM * req.paceSecPerKm() / 1000,
        gain,
        profile,
        newRoadM,
        newRoadRatio,
        tags,
        path,
        segmentIdsOf(edges),
        milestones);
  }

  private record Segments(List<Piece> pieces, double parkM, double riverM) {}

  // 점 사이 구간마다 종류를 정한다: 공원·하천 > 새 길 > 이름 있는 도로 (D9)
  private Segments segments(
      PointList pl, Map<String, List<PathDetail>> details, Set<Long> explored) {
    int n = pl.size() - 1;
    Object[] edge = spread(details.get(EDGE_ID), n);
    Object[] street = spread(details.get(STREET_NAME), n);
    Object[] park = spread(details.get(AreaEncodedValues.NEAR_PARK), n);
    Object[] river = spread(details.get(AreaEncodedValues.NEAR_RIVER), n);
    List<Piece> pieces = new ArrayList<>();
    double cum = 0;
    double parkM = 0;
    double riverM = 0;
    for (int i = 0; i < n; i++) {
      double len = DIST.calcDist(pl.getLat(i), pl.getLon(i), pl.getLat(i + 1), pl.getLon(i + 1));
      double midLat = (pl.getLat(i) + pl.getLat(i + 1)) / 2;
      double midLng = (pl.getLon(i) + pl.getLon(i + 1)) / 2;
      MilestoneKind kind = null;
      String name = null;
      if (Boolean.TRUE.equals(park[i])) {
        parkM += len;
        kind = MilestoneKind.PARK;
        name = areaNames.nameAt(midLat, midLng, "park").orElse(null);
      }
      if (Boolean.TRUE.equals(river[i])) {
        riverM += len;
        if (kind == null) {
          kind = MilestoneKind.RIVER;
          name = areaNames.nameAt(midLat, midLng, "river").orElse(null);
        }
      }
      if (kind == null && explored != null && isNew(edge[i], explored)) {
        kind = MilestoneKind.NEW_ROAD;
      }
      if (kind == null && street[i] instanceof String s && !s.isBlank()) {
        kind = MilestoneKind.ROAD;
        name = s;
      }
      int start = (int) Math.round(cum);
      cum += len;
      if (kind != null) {
        pieces.add(new Piece(kind, name, start, (int) Math.round(cum)));
      }
    }
    return new Segments(pieces, parkM, riverM);
  }

  private boolean isNew(Object edge, Set<Long> explored) {
    long id = edge instanceof Number e ? segmentIds.segmentOf(e.intValue()) : -1;
    return id >= 0 && !explored.contains(id);
  }

  // 구간 [first, last) 로 나뉜 path detail → 점 사이 구간 i 마다 값
  private static Object[] spread(List<PathDetail> list, int n) {
    Object[] out = new Object[n];
    for (PathDetail d : list) {
      for (int i = d.getFirst(); i < d.getLast() && i < n; i++) {
        out[i] = d.getValue();
      }
    }
    return out;
  }

  // 탐험 목록에 없는 구간 길이 합. 같은 구간을 되짚어도 1회만 (명세 §3.2)
  private int newRoadM(PointList pl, List<PathDetail> edges, Set<Long> explored) {
    Set<Long> counted = new HashSet<>();
    double sum = 0;
    for (PathDetail d : edges) {
      long id = segmentIds.segmentOf(((Number) d.getValue()).intValue());
      if (id < 0 || explored.contains(id) || !counted.add(id)) {
        continue;
      }
      for (int i = d.getFirst(); i < d.getLast(); i++) {
        sum += DIST.calcDist(pl.getLat(i), pl.getLon(i), pl.getLat(i + 1), pl.getLon(i + 1));
      }
    }
    return (int) Math.round(sum);
  }

  private List<Long> segmentIdsOf(List<PathDetail> edges) {
    Set<Long> ids = new LinkedHashSet<>();
    for (PathDetail d : edges) {
      long id = segmentIds.segmentOf(((Number) d.getValue()).intValue());
      if (id >= 0) {
        ids.add(id);
      }
    }
    return List.copyOf(ids);
  }

  private static List<double[]> lngLat(PointList pl) {
    List<double[]> out = new ArrayList<>(pl.size());
    for (int i = 0; i < pl.size(); i++) {
      out.add(new double[] {round6(pl.getLon(i)), round6(pl.getLat(i))});
    }
    return out;
  }

  private static double round6(double v) {
    return Math.round(v * 1e6) / 1e6;
  }
}

package com.runmap.api.route.service;

import com.graphhopper.ResponsePath;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.PointList;
import com.graphhopper.util.details.PathDetail;
import com.runmap.api.map.domain.SegmentIds;
import com.runmap.api.route.dto.RouteRecommendRequest;
import com.runmap.api.route.dto.RouteRecommendResponse.RouteDto;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** GraphHopper 경로 하나 → 응답 코스 하나. */
@Component
public class RouteAssembler {

  static final String EDGE_ID = "edge_id";
  private static final DistanceCalc DIST = DistanceCalcEarth.DIST_EARTH;

  private final SegmentIds segmentIds;

  public RouteAssembler(SegmentIds segmentIds) {
    this.segmentIds = segmentIds;
  }

  public RouteDto toDto(ResponsePath p, String type, RouteRecommendRequest req) {
    List<double[]> path = lngLat(p.getPoints());
    int distanceM = (int) Math.round(p.getDistance());
    List<PathDetail> edges = p.getPathDetails().get(EDGE_ID);
    Integer newRoadM =
        req.exploredSegmentIds() == null
            ? null
            : newRoadM(p.getPoints(), edges, new HashSet<>(req.exploredSegmentIds()));
    Double newRoadRatio =
        newRoadM == null || distanceM == 0
            ? null
            : Math.min(1.0, Math.round(newRoadM * 100.0 / distanceM) / 100.0);
    String routeId = String.format("r_%08x", Arrays.deepHashCode(path.toArray(new double[0][])));
    return new RouteDto(
        routeId,
        type,
        distanceM,
        distanceM * req.paceSecPerKm() / 1000,
        null,
        null,
        newRoadM,
        newRoadRatio,
        List.of(),
        path,
        segments(edges),
        List.of());
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

  private List<Long> segments(List<PathDetail> edges) {
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

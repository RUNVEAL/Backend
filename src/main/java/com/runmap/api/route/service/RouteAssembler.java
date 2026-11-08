package com.runmap.api.route.service;

import com.graphhopper.ResponsePath;
import com.graphhopper.util.PointList;
import com.graphhopper.util.details.PathDetail;
import com.runmap.api.map.domain.SegmentIds;
import com.runmap.api.route.dto.RouteRecommendRequest;
import com.runmap.api.route.dto.RouteRecommendResponse.RouteDto;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** GraphHopper 경로 하나 → 응답 코스 하나. */
@Component
public class RouteAssembler {

  static final String EDGE_ID = "edge_id";

  private final SegmentIds segmentIds;

  public RouteAssembler(SegmentIds segmentIds) {
    this.segmentIds = segmentIds;
  }

  public RouteDto toDto(ResponsePath p, String type, RouteRecommendRequest req) {
    List<double[]> path = lngLat(p.getPoints());
    int distanceM = (int) Math.round(p.getDistance());
    String routeId = String.format("r_%08x", Arrays.deepHashCode(path.toArray(new double[0][])));
    return new RouteDto(
        routeId,
        type,
        distanceM,
        distanceM * req.paceSecPerKm() / 1000,
        null,
        null,
        null,
        null,
        List.of(),
        path,
        segments(p.getPathDetails().get(EDGE_ID)),
        List.of());
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

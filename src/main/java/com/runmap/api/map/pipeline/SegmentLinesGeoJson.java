package com.runmap.api.map.pipeline;

import com.graphhopper.GraphHopper;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PointList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/**
 * 구 팩의 "길 조각 선" — 각 segment를 MultiLineString(그 segment에 속한 edge들의 geometry)으로, 속성에 {@code
 * segmentId}·{@code lengthM}을 담아 GeoJSON으로 쓴다.
 */
public final class SegmentLinesGeoJson {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private record Geometry(String type, Object coordinates) {}

  private record Feature(String type, Geometry geometry, Map<String, Object> properties) {}

  private record Collection(String type, List<Feature> features) {}

  private SegmentLinesGeoJson() {}

  public static void write(Path out, GraphHopper gh, long[] segmentIdByEdge) throws IOException {
    BaseGraph graph = gh.getBaseGraph();

    Map<Long, List<Integer>> edgesBySegment = new LinkedHashMap<>();
    for (int edge = 0; edge < segmentIdByEdge.length; edge++) {
      long segmentId = segmentIdByEdge[edge];
      if (segmentId >= 0) {
        edgesBySegment.computeIfAbsent(segmentId, key -> new ArrayList<>()).add(edge);
      }
    }

    List<Feature> features = new ArrayList<>();
    edgesBySegment.forEach(
        (segmentId, edges) -> {
          List<List<double[]>> lines = new ArrayList<>();
          double lengthM = 0;
          for (int edge : edges) {
            EdgeIteratorState state = graph.getEdgeIteratorState(edge, Integer.MIN_VALUE);
            lines.add(toLine(state.fetchWayGeometry(FetchMode.ALL)));
            lengthM += state.getDistance();
          }
          Map<String, Object> props =
              Map.of("segmentId", segmentId, "lengthM", Math.round(lengthM));
          features.add(new Feature("Feature", new Geometry("MultiLineString", lines), props));
        });

    Files.writeString(
        out, MAPPER.writeValueAsString(new Collection("FeatureCollection", features)));
  }

  private static List<double[]> toLine(PointList points) {
    List<double[]> line = new ArrayList<>(points.size());
    for (int i = 0; i < points.size(); i++) {
      line.add(new double[] {points.getLon(i), points.getLat(i)});
    }
    return line;
  }
}

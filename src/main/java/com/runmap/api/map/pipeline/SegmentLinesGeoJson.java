package com.runmap.api.map.pipeline;

import com.graphhopper.GraphHopper;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PointList;
import com.runmap.api.map.domain.AdminAreas;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 구 팩의 "길 조각 선" — 각 segment를 MultiLineString(그 segment에 속한 edge들의 geometry)으로, 속성에 {@code
 * segmentId}·{@code lengthM}을 담아 GeoJSON으로 쓴다. {@link #writeByGu}는 구별 파일로 쪼갠다.
 */
public final class SegmentLinesGeoJson {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private record Geometry(String type, Object coordinates) {}

  private record Feature(String type, Geometry geometry, Map<String, Object> properties) {}

  private record Collection(String type, List<Feature> features) {}

  private SegmentLinesGeoJson() {}

  /** 모든 segment를 하나의 GeoJSON FeatureCollection으로 쓴다. */
  public static void write(Path out, GraphHopper gh, long[] segmentIdByEdge) throws IOException {
    BaseGraph graph = gh.getBaseGraph();
    List<Feature> features = new ArrayList<>();
    groupEdges(segmentIdByEdge)
        .forEach((segmentId, edges) -> features.add(buildFeature(graph, segmentId, edges)));
    writeCollection(out, features);
  }

  /** segment를 구별로 나눠 {@code segments-{gu}.geojson}로 쓴다. 어느 구에도 안 속한 segment는 건너뛴다. */
  public static List<String> writeByGu(
      Path outDir, GraphHopper gh, long[] segmentIdByEdge, AdminAreas areas) throws IOException {
    BaseGraph graph = gh.getBaseGraph();
    NodeAccess nodes = graph.getNodeAccess();
    Map<Long, List<Integer>> edgesBySegment = groupEdges(segmentIdByEdge);

    // 1) segmentId → 구 (지오메트리 없이 — 메모리 가볍게)
    Map<String, List<Long>> segmentsByGu = new LinkedHashMap<>();
    edgesBySegment.forEach(
        (segmentId, edges) ->
            guOf(graph, nodes, edges.get(0), areas)
                .ifPresent(
                    gu ->
                        segmentsByGu.computeIfAbsent(gu, key -> new ArrayList<>()).add(segmentId)));

    // 2) 구 단위로 지오메트리 생성·기록 (전국이어도 한 구 분량만 메모리에 올렸다 해제)
    for (Map.Entry<String, List<Long>> entry : segmentsByGu.entrySet()) {
      List<Feature> features = new ArrayList<>(entry.getValue().size());
      for (long segmentId : entry.getValue()) {
        features.add(buildFeature(graph, segmentId, edgesBySegment.get(segmentId)));
      }
      writeCollection(outDir.resolve("segments-" + entry.getKey() + ".geojson"), features);
    }
    return List.copyOf(segmentsByGu.keySet());
  }

  private static void writeCollection(Path out, List<Feature> features) throws IOException {
    Files.writeString(
        out, MAPPER.writeValueAsString(new Collection("FeatureCollection", features)));
  }

  private static Map<Long, List<Integer>> groupEdges(long[] segmentIdByEdge) {
    Map<Long, List<Integer>> edgesBySegment = new LinkedHashMap<>();
    for (int edge = 0; edge < segmentIdByEdge.length; edge++) {
      long segmentId = segmentIdByEdge[edge];
      if (segmentId >= 0) {
        edgesBySegment.computeIfAbsent(segmentId, key -> new ArrayList<>()).add(edge);
      }
    }
    return edgesBySegment;
  }

  private static Feature buildFeature(BaseGraph graph, long segmentId, List<Integer> edges) {
    List<List<double[]>> lines = new ArrayList<>();
    double lengthM = 0;
    for (int edge : edges) {
      EdgeIteratorState state = graph.getEdgeIteratorState(edge, Integer.MIN_VALUE);
      lines.add(toLine(state.fetchWayGeometry(FetchMode.ALL)));
      lengthM += state.getDistance();
    }
    Map<String, Object> properties = Map.of("segmentId", segmentId, "lengthM", Math.round(lengthM));
    return new Feature("Feature", new Geometry("MultiLineString", lines), properties);
  }

  private static Optional<String> guOf(
      BaseGraph graph, NodeAccess nodes, int edge, AdminAreas areas) {
    EdgeIteratorState state = graph.getEdgeIteratorState(edge, Integer.MIN_VALUE);
    double lat = (nodes.getLat(state.getBaseNode()) + nodes.getLat(state.getAdjNode())) / 2;
    double lng = (nodes.getLon(state.getBaseNode()) + nodes.getLon(state.getAdjNode())) / 2;
    return areas.guAt(lat, lng);
  }

  private static List<double[]> toLine(PointList points) {
    List<double[]> line = new ArrayList<>(points.size());
    for (int i = 0; i < points.size(); i++) {
      line.add(new double[] {points.getLon(i), points.getLat(i)});
    }
    return line;
  }
}

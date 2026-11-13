package com.runmap.api.map.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 전국 인덱스 GeoJSON — 행정동 경계 폴리곤에 동별 총 길이({@code lengthM})를 주입하고, 경계를 Douglas-Peucker로 단순화해 쓴다.
 *
 * <p>경계는 줌아웃 overview 용도라 촘촘할 필요가 없다. 원본(동당 평균 ~200점)을 그대로 쓰면 전국 한 파일이 수십 MB라 앱 렌더링이 터진다. 점을 줄여 전국
 * 한 파일을 유지하면서도 크기를 크게 낮춘다. (줌인하면 경계 대신 길이 보이므로 정밀도 손실은 체감되지 않는다.)
 */
public final class IndexGeoJson {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  /** 경계 단순화 허용 오차(도). 약 55m — 줌아웃 overview 전용이라 이 정도도 체감 손실이 없고 점 수를 크게 줄인다. */
  private static final double SIMPLIFY_TOLERANCE_DEG = 0.0005;

  /** 닫힌 유효 링의 최소 점 수. 이보다 줄면 원본을 유지한다. */
  private static final int MIN_RING_POINTS = 4;

  private IndexGeoJson() {}

  public static void write(Path out, Path sourceDongGeoJson, MapIndex index) throws IOException {
    JsonNode root = MAPPER.readTree(Files.readString(sourceDongGeoJson));
    for (JsonNode feature : root.get("features")) {
      ObjectNode properties = (ObjectNode) feature.get("properties");
      Long lengthM = index.dongLengthM().get(properties.get("code").asString());
      if (lengthM != null) {
        properties.put("lengthM", lengthM);
      }
      simplifyGeometry((ObjectNode) feature.get("geometry"));
    }
    Files.writeString(out, MAPPER.writeValueAsString(root));
  }

  /** 경계 geometry(Polygon·MultiPolygon)의 각 링을 단순화한다. */
  private static void simplifyGeometry(ObjectNode geometry) {
    if (geometry == null) {
      return;
    }
    String type = geometry.get("type").asString();
    JsonNode coordinates = geometry.get("coordinates");
    if ("Polygon".equals(type)) {
      geometry.set("coordinates", simplifyPolygon((ArrayNode) coordinates));
    } else if ("MultiPolygon".equals(type)) {
      ArrayNode polygons = MAPPER.createArrayNode();
      for (JsonNode polygon : coordinates) {
        polygons.add(simplifyPolygon((ArrayNode) polygon));
      }
      geometry.set("coordinates", polygons);
    }
  }

  private static ArrayNode simplifyPolygon(ArrayNode rings) {
    ArrayNode out = MAPPER.createArrayNode();
    for (JsonNode ring : rings) {
      out.add(simplifyRing((ArrayNode) ring));
    }
    return out;
  }

  private static ArrayNode simplifyRing(ArrayNode ring) {
    int n = ring.size();
    double[][] pts = new double[n][2];
    for (int i = 0; i < n; i++) {
      pts[i][0] = ring.get(i).get(0).asDouble();
      pts[i][1] = ring.get(i).get(1).asDouble();
    }
    boolean[] keep = new boolean[n];
    if (n <= MIN_RING_POINTS) {
      java.util.Arrays.fill(keep, true);
    } else {
      keep[0] = true;
      keep[n - 1] = true;
      douglasPeucker(pts, 0, n - 1, keep);
      int kept = 0;
      for (boolean b : keep) {
        if (b) {
          kept++;
        }
      }
      if (kept < MIN_RING_POINTS) {
        java.util.Arrays.fill(keep, true); // 너무 줄면 원본 점 유지 (유효 폴리곤 보장)
      }
    }
    ArrayNode out = MAPPER.createArrayNode();
    for (int i = 0; i < n; i++) {
      if (keep[i]) {
        ArrayNode point = MAPPER.createArrayNode();
        point.add(round6(pts[i][0])); // 좌표를 6자리(~0.1m)로 — 과한 정밀도 제거, 모양 변화 없음
        point.add(round6(pts[i][1]));
        out.add(point);
      }
    }
    return out;
  }

  private static double round6(double v) {
    return Math.round(v * 1e6) / 1e6;
  }

  private static void douglasPeucker(double[][] pts, int lo, int hi, boolean[] keep) {
    if (hi <= lo + 1) {
      return;
    }
    double max = -1;
    int farthest = -1;
    for (int i = lo + 1; i < hi; i++) {
      double d = perpendicularDistance(pts[i], pts[lo], pts[hi]);
      if (d > max) {
        max = d;
        farthest = i;
      }
    }
    if (max > SIMPLIFY_TOLERANCE_DEG) {
      keep[farthest] = true;
      douglasPeucker(pts, lo, farthest, keep);
      douglasPeucker(pts, farthest, hi, keep);
    }
  }

  private static double perpendicularDistance(double[] p, double[] a, double[] b) {
    double dx = b[0] - a[0];
    double dy = b[1] - a[1];
    if (dx == 0 && dy == 0) {
      return Math.hypot(p[0] - a[0], p[1] - a[1]);
    }
    double t = ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / (dx * dx + dy * dy);
    double projX = a[0] + t * dx;
    double projY = a[1] + t * dy;
    return Math.hypot(p[0] - projX, p[1] - projY);
  }
}

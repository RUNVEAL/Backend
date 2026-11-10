package com.runmap.api.map.pipeline;

import com.graphhopper.GraphHopper;
import com.runmap.api.map.config.MapProperties;
import com.runmap.api.map.domain.AdminAreas;
import com.runmap.api.map.domain.MapGraphFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 오프라인 파이프라인 도구: OSM 그래프를 빌드해 지도 산출물을 생성한다 — {@code segments.bin}(edge↔segment), {@code
 * index.json}(동/구별 총 길이), {@code index.geojson}(동 경계+총 길이), {@code segments.geojson}(길 조각 선).
 * gradle task {@code generateSegments}로 실행한다.
 *
 * <p>설정은 시스템 프로퍼티 {@code runveal.map.*}로 받는다. Spring·DB 없이 그래프만 쓴다.
 */
public final class SegmentGenerator {

  private SegmentGenerator() {}

  public static void main(String[] args) throws IOException {
    String version = System.getProperty("runveal.map.version", "dev");
    String graphLocation = require("runveal.map.graph-location");
    String adminDir = require("runveal.map.admin-dir");
    MapProperties props =
        new MapProperties(
            version,
            require("runveal.map.osm-file"),
            graphLocation,
            require("runveal.map.srtm-dir"),
            require("runveal.map.areas-dir"));

    GraphHopper gh = MapGraphFactory.create(props).importOrLoad();
    try {
      long[] segmentIdByEdge = SegmentBuilder.build(gh);
      AdminAreas areas = new AdminAreas(adminDir);
      MapIndex index = MapIndex.from(version, RegionTotals.compute(gh, areas, segmentIdByEdge));

      Path dir = Path.of(graphLocation);
      SegmentsFile.write(dir.resolve("segments.bin"), segmentIdByEdge);
      MapIndexFile.write(dir.resolve("index.json"), index);
      IndexGeoJson.write(dir.resolve("index.geojson"), Path.of(adminDir, "dong.geojson"), index);
      List<String> gus = SegmentLinesGeoJson.writeByGu(dir, gh, segmentIdByEdge, areas);
      List<String> files = new ArrayList<>(List.of("segments.bin", "index.json", "index.geojson"));
      gus.forEach(gu -> files.add("segments-" + gu + ".geojson"));
      ManifestFile.write(dir.resolve("manifest.json"), Manifest.of(version, files));

      long segments = Arrays.stream(segmentIdByEdge).filter(s -> s >= 0).distinct().count();
      System.out.printf(
          "파이프라인 완료: edge %d → segment %d, 동 %d개 → %s%n",
          segmentIdByEdge.length, segments, index.dongLengthM().size(), dir);
    } finally {
      gh.close();
    }
  }

  private static String require(String key) {
    String value = System.getProperty(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("필수 설정 누락: -D" + key);
    }
    return value;
  }
}

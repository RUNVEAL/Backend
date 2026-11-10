package com.runmap.api.map.pipeline;

import com.graphhopper.GraphHopper;
import com.runmap.api.map.config.MapProperties;
import com.runmap.api.map.domain.MapGraphFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * 오프라인 파이프라인 도구: OSM 그래프를 빌드해 {@code segments.bin}(그래프 디렉터리)을 생성한다. gradle task {@code
 * generateSegments}로 실행한다.
 *
 * <p>설정은 시스템 프로퍼티 {@code runveal.map.*}로 받는다 (gradle task가 주입). Spring·DB 없이 그래프만 쓴다.
 */
public final class SegmentGenerator {

  private SegmentGenerator() {}

  public static void main(String[] args) throws IOException {
    MapProperties props =
        new MapProperties(
            System.getProperty("runveal.map.version", "dev"),
            require("runveal.map.osm-file"),
            require("runveal.map.graph-location"),
            require("runveal.map.srtm-dir"),
            require("runveal.map.areas-dir"));

    GraphHopper gh = MapGraphFactory.create(props).importOrLoad();
    try {
      long[] segmentIdByEdge = SegmentBuilder.build(gh);
      Path out = Path.of(props.graphLocation(), "segments.bin");
      SegmentsFile.write(out, segmentIdByEdge);
      long segments = Arrays.stream(segmentIdByEdge).distinct().count();
      System.out.printf(
          "segments.bin 생성: edge %d개 -> segment %d개 (%s)%n", segmentIdByEdge.length, segments, out);
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

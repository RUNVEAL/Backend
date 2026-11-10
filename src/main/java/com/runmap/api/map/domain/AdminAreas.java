package com.runmap.api.map.domain;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.AreaIndex;
import com.graphhopper.routing.util.CustomArea;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * 좌표 → 행정동/구 코드. 그래프와 별개로, 공공데이터 행정동 경계 GeoJSON을 읽어 point-in-polygon으로 찾는다 ({@link AreaNames}와 같은
 * AreaIndex 방식). GeoJSON feature properties: {@code code}(행정동), {@code gu}(구). 경로가 비었거나 없으면 빈
 * 조회(graceful).
 */
public final class AdminAreas {

  private final AreaIndex<CustomArea> index;

  public AdminAreas(String geojsonDir) {
    this.index = new AreaIndex<>(loadAreas(geojsonDir));
  }

  private static List<CustomArea> loadAreas(String geojsonDir) {
    if (geojsonDir == null || geojsonDir.isBlank() || !Files.isDirectory(Path.of(geojsonDir))) {
      return List.of();
    }
    return GraphHopper.resolveCustomAreas(geojsonDir).getFeatures().stream()
        .map(CustomArea::fromJsonFeature)
        .toList();
  }

  /** 그 좌표가 속한 행정동 코드. 경계 밖이면 empty. */
  public Optional<String> codeAt(double lat, double lng) {
    return property(lat, lng, "code");
  }

  /** 그 좌표가 속한 구 코드. */
  public Optional<String> guAt(double lat, double lng) {
    return property(lat, lng, "gu");
  }

  private Optional<String> property(double lat, double lng, String key) {
    return index.query(lat, lng).stream()
        .map(area -> area.getProperties().get(key))
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .findFirst();
  }
}

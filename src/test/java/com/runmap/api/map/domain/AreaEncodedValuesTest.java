package com.runmap.api.map.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.runmap.api.map.config.MapProperties;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * near_park/near_river 는 custom_areas 없이, import 직후 {@link AreaEncodedValues#bake}가 GeoJSON을 직접 읽어
 * 그래프에 굽는다. 값은 저장되므로 이후 load는 areas 없이도 그대로 동작한다(런타임 OOM·지연 회피의 근거).
 */
class AreaEncodedValuesTest {

  private static MapProperties props(Path graphDir) {
    return new MapProperties(
        "test",
        "src/test/resources/fixtures/seogyo.osm.pbf",
        graphDir.toString(),
        "src/test/resources/fixtures/srtm",
        "src/test/resources/fixtures/areas");
  }

  private static long nearParkCount(GraphHopper gh) {
    BooleanEncodedValue ev =
        gh.getEncodingManager().getBooleanEncodedValue(AreaEncodedValues.NEAR_PARK);
    AllEdgesIterator edge = gh.getBaseGraph().getAllEdges();
    long count = 0;
    while (edge.next()) {
      if (edge.get(ev)) {
        count++;
      }
    }
    return count;
  }

  @Test
  void bakeSetsNearParkFromGeoJson(@TempDir Path graphDir) {
    GraphHopper gh = MapGraphFactory.create(props(graphDir)).importOrLoad();
    try {
      assertThat(nearParkCount(gh)).as("import 직후엔 비어 있어야 한다(custom_areas 안 씀)").isZero();

      AreaEncodedValues.bake(gh, "src/test/resources/fixtures/areas");

      assertThat(nearParkCount(gh)).as("bake 후 공원 근처 edge가 생겨야 한다").isGreaterThan(0);
    } finally {
      gh.close();
    }
  }

  @Test
  void loadPreservesBakedValuesWithoutAreas(@TempDir Path graphDir) {
    GraphHopper built = MapGraphFactory.create(props(graphDir)).importOrLoad();
    long baked;
    try {
      AreaEncodedValues.bake(built, "src/test/resources/fixtures/areas"); // flush 로 저장됨
      baked = nearParkCount(built);
    } finally {
      built.close();
    }
    assertThat(baked).isGreaterThan(0);

    // 다시 연다: custom_areas 를 전혀 안 써도(프로파일 일치) load 되고 near_park 가 보존돼야 한다
    GraphHopper loaded = MapGraphFactory.create(props(graphDir)).importOrLoad();
    try {
      assertThat(nearParkCount(loaded)).isEqualTo(baked);
    } finally {
      loaded.close();
    }
  }
}

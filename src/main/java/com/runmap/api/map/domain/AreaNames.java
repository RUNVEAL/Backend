package com.runmap.api.map.domain;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.AreaIndex;
import com.graphhopper.routing.util.CustomArea;
import com.runmap.api.map.config.MapProperties;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 좌표 → 공원·하천 이름 (경로 정보 milestones 용). 그래프와 같은 GeoJSON 을 읽는다. */
@Component
public class AreaNames {

  private final AreaIndex<CustomArea> index;

  public AreaNames(MapProperties props) {
    this.index =
        new AreaIndex<>(
            GraphHopper.resolveCustomAreas(props.areasDir()).getFeatures().stream()
                .map(CustomArea::fromJsonFeature)
                .toList());
  }

  /**
   * @param kind "park" 또는 "river"
   */
  public Optional<String> nameAt(double lat, double lng, String kind) {
    return index.query(lat, lng).stream()
        .filter(a -> kind.equals(a.getProperties().get("kind")))
        .map(a -> a.getProperties().get("name"))
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .findFirst();
  }
}

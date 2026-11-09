package com.runmap.api.map.domain;

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DefaultImportRegistry;
import com.graphhopper.routing.ev.ImportRegistry;
import com.graphhopper.routing.ev.ImportUnit;
import com.graphhopper.routing.ev.SimpleBooleanEncodedValue;
import com.graphhopper.routing.util.CustomArea;
import java.util.List;
import java.util.Map;

/**
 * 공원·하천 근처 길 표시(boolean EV). GraphHopper가 import 때 각 way에 붙여 주는 custom_areas 중 GeoJSON
 * properties.kind 가 일치하는 것이 있으면 true.
 *
 * <p>EV 이름은 "in_"으로 시작할 수 없다(GH가 area 조건식용으로 예약).
 */
public final class AreaEncodedValues {

  public static final String NEAR_PARK = "near_park";
  public static final String NEAR_RIVER = "near_river";

  private static final Map<String, String> KIND_BY_EV =
      Map.of(NEAR_PARK, "park", NEAR_RIVER, "river");

  private AreaEncodedValues() {}

  /** 기본 레지스트리에 near_park · near_river 를 더한 것. */
  public static ImportRegistry registry() {
    ImportRegistry defaults = new DefaultImportRegistry();
    return name -> {
      String kind = KIND_BY_EV.get(name);
      if (kind == null) {
        return defaults.createImportUnit(name);
      }
      return ImportUnit.create(
          name,
          props -> new SimpleBooleanEncodedValue(name, false),
          (lookup, props) -> {
            BooleanEncodedValue ev = lookup.getBooleanEncodedValue(name);
            return (edgeId, edgeIntAccess, way, relationFlags) -> {
              List<CustomArea> areas = way.getTag("custom_areas", List.of());
              boolean near =
                  areas.stream().anyMatch(a -> kind.equals(a.getProperties().get("kind")));
              ev.setBool(false, edgeId, edgeIntAccess, near);
            };
          });
    };
  }
}

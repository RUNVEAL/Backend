package com.runmap.api.map.domain;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DefaultImportRegistry;
import com.graphhopper.routing.ev.ImportRegistry;
import com.graphhopper.routing.ev.ImportUnit;
import com.graphhopper.routing.ev.SimpleBooleanEncodedValue;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.AreaIndex;
import com.graphhopper.routing.util.CustomArea;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import java.util.List;

/**
 * 공원·하천 근처 길 표시(boolean EV) {@code near_park}·{@code near_river}.
 *
 * <p>GraphHopper의 {@code custom_areas.directory}를 쓰지 않는다 — 그 설정은 폴리곤을 프로파일에 묶어, 런타임 routing 때 전국 수백
 * MB를 요청마다 직렬화해 OOM·지연을 일으킨다(A·B 공동 계약 C3). 대신 EV는 import 때 비워 두고(no-op), import 직후 {@link #bake}가
 * 공원/하천 GeoJSON을 직접 읽어 각 edge에 값을 채운다. 값은 그래프에 저장되므로 이후 load는 areas 없이도 동작한다.
 *
 * <p>EV 이름은 "in_"으로 시작할 수 없다(GH가 area 조건식용으로 예약).
 */
public final class AreaEncodedValues {

  public static final String NEAR_PARK = "near_park";
  public static final String NEAR_RIVER = "near_river";

  private AreaEncodedValues() {}

  /** 기본 레지스트리에 near_park · near_river EV를 더한 것. 값은 import 때 채우지 않고 {@link #bake}가 채운다. */
  public static ImportRegistry registry() {
    ImportRegistry defaults = new DefaultImportRegistry();
    return name -> {
      if (!NEAR_PARK.equals(name) && !NEAR_RIVER.equals(name)) {
        return defaults.createImportUnit(name);
      }
      return ImportUnit.create(
          name,
          props -> new SimpleBooleanEncodedValue(name, false),
          // import 단계에서는 값을 세팅하지 않는다(지오메트리 없음). bake()가 채운다.
          (lookup, props) -> (edgeId, edgeIntAccess, way, relationFlags) -> {});
    };
  }

  /**
   * import 직후 호출: 공원/하천 GeoJSON을 읽어 각 edge 중점이 공원/하천 안이면 near_park/near_river를 true로 세팅하고 저장한다.
   * load된(이미 구운) 그래프에는 호출하지 않는다. areasDir가 비었으면 아무것도 하지 않는다(전부 false).
   */
  public static void bake(GraphHopper gh, String areasDir) {
    if (areasDir == null || areasDir.isBlank()) {
      return;
    }
    List<CustomArea> areas =
        GraphHopper.resolveCustomAreas(areasDir).getFeatures().stream()
            .map(CustomArea::fromJsonFeature)
            .toList();
    if (areas.isEmpty()) {
      return;
    }
    AreaIndex<CustomArea> index = new AreaIndex<>(areas);
    BaseGraph graph = gh.getBaseGraph();
    NodeAccess nodes = graph.getNodeAccess();
    EncodingManager em = gh.getEncodingManager();
    BooleanEncodedValue parkEv = em.getBooleanEncodedValue(NEAR_PARK);
    BooleanEncodedValue riverEv = em.getBooleanEncodedValue(NEAR_RIVER);

    AllEdgesIterator edge = graph.getAllEdges();
    while (edge.next()) {
      double lat = (nodes.getLat(edge.getBaseNode()) + nodes.getLat(edge.getAdjNode())) / 2;
      double lng = (nodes.getLon(edge.getBaseNode()) + nodes.getLon(edge.getAdjNode())) / 2;
      for (CustomArea area : index.query(lat, lng)) {
        Object kind = area.getProperties().get("kind");
        if ("park".equals(kind)) {
          edge.set(parkEv, true);
        } else if ("river".equals(kind)) {
          edge.set(riverEv, true);
        }
      }
    }
    graph.flush(); // EV 변경을 그래프 파일에 저장 → 이후 load는 areas 불필요
  }
}

package com.runmap.api.map.domain;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.index.Snap;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 좌표 → 가까운 도로명. 그래프의 LocationIndex 로 좌표에서 가장 가까운 "이름 있는 도로"를 찾는다.
 *
 * <p>주인: 우선님. 출발·도착 지점 표시용. OSM 에 이름 없는 길(골목·샛길)이 많아 가까운 도로가 없을 수 있고, 너무 먼 도로는 "근처"라 부르기 어려워 제외한다 —
 * 이때는 empty 이고, 앱은 동네 이름으로 대체한다. 번지(도로명주소)는 OSM 에 없어 담지 않는다.
 */
@Component
public class NearbyRoads {

  /** 이보다 멀면 "근처 도로"라 부르기 어려워 이름을 주지 않는다. {@code RouteAssembler} 와 같은 기준(300m). */
  private static final double MAX_DISTANCE_M = 300;

  private final GraphHopper gh;

  public NearbyRoads(GraphHopper gh) {
    this.gh = gh;
  }

  /** 좌표에서 {@value #MAX_DISTANCE_M}m 안의 가장 가까운 이름 있는 도로명. 없으면 empty. */
  public Optional<String> nameAt(double lat, double lng) {
    EdgeFilter named = e -> e.getName() != null && !e.getName().isBlank();
    Snap snap = gh.getLocationIndex().findClosest(lat, lng, named);
    if (!snap.isValid() || snap.getQueryDistance() > MAX_DISTANCE_M) {
      return Optional.empty();
    }
    return Optional.of(snap.getClosestEdge().getName());
  }
}

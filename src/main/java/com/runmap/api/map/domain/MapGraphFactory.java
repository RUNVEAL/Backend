package com.runmap.api.map.domain;

import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.WeightingFactory;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.GHUtility;
import com.runmap.api.map.config.MapProperties;
import java.util.List;
import java.util.Set;

/**
 * GraphHopper 설정의 단 한 곳 (PoC poc/gh/config.yml 이식). 맵매칭·파이프라인(우선님)도 이 메서드로 같은 그래프를 만든다.
 *
 * <p>여기를 바꾸면 기존 그래프 캐시와 맞지 않으므로 graphLocation 을 지우고 다시 import 해야 한다. 변경은 A·B 공동 승인(계약 C3).
 */
public final class MapGraphFactory {

  public static final String PROFILE = "foot";

  /** 요청 hint: 벌점을 줄 엣지 집합 (Set&lt;Integer&gt;, PMap.putObject). */
  public static final String EXPLORED_EDGES_HINT = "runveal.explored_edges";

  static final String ENCODED_VALUES =
      "foot_access, foot_priority, foot_average_speed, foot_road_access, hike_rating,"
          + " average_slope, road_class, road_environment, mtb_rating, country, "
          + AreaEncodedValues.NEAR_PARK
          + ", "
          + AreaEncodedValues.NEAR_RIVER;

  private MapGraphFactory() {}

  /** 설정만 끝낸 GraphHopper. 호출한 쪽이 importOrLoad() 한다. */
  public static GraphHopper create(MapProperties p) {
    GraphHopperConfig config = new GraphHopperConfig();
    config.putObject("datareader.file", p.osmFile());
    config.putObject("graph.location", p.graphLocation());
    config.putObject("graph.encoded_values", ENCODED_VALUES);
    config.putObject("graph.elevation.provider", "srtm");
    config.putObject("graph.elevation.cache_dir", p.srtmDir());
    config.putObject("graph.elevation.interpolate", "bilinear");
    config.putObject("graph.elevation.edge_smoothing", "moving_average");
    config.putObject("graph.elevation.edge_smoothing.moving_average.window_size", 150);
    config.putObject("import.osm.ignored_highways", "motorway, trunk");
    config.putObject("routing.snap_preventions_default", "tunnel, bridge, ferry");
    config.putObject("routing.max_visited_nodes", 1_500_000);
    // custom_areas.directory 는 쓰지 않는다 — 폴리곤이 프로파일에 묶여 런타임 routing 때 전국 수백 MB를
    // 요청마다 직렬화해 OOM·지연을 유발한다. near_park·near_river 는 import 직후 AreaEncodedValues.bake 가 채운다.
    CustomModel foot =
        CustomModel.merge(
            GHUtility.loadCustomModelFromJar("foot.json"),
            GHUtility.loadCustomModelFromJar("foot_elevation.json"));
    config.setProfiles(List.of(new Profile(PROFILE).setCustomModel(foot)));
    // LM: 요청마다 custom model 을 바꿔도 빠르다 (CH는 요청별 가중치 불가)
    config.setLMProfiles(List.of(new LMProfile(PROFILE)));

    GraphHopper gh =
        new GraphHopper() {
          @Override
          protected WeightingFactory createWeightingFactory() {
            WeightingFactory base = super.createWeightingFactory();
            return (profile, hints, disableTurnCosts) -> {
              Weighting w = base.createWeighting(profile, hints, disableTurnCosts);
              Set<Integer> explored = hints.getObject(EXPLORED_EDGES_HINT, null);
              return explored == null ? w : new ExploredPenaltyWeighting(w, explored);
            };
          }
        };
    gh.setImportRegistry(AreaEncodedValues.registry()); // init 전에
    return gh.init(config);
  }
}

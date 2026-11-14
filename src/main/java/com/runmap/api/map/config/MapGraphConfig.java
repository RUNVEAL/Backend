package com.runmap.api.map.config;

import com.graphhopper.GraphHopper;
import com.runmap.api.map.domain.AreaEncodedValues;
import com.runmap.api.map.domain.MapGraphFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 기동 시 그래프를 import(처음) 또는 load(캐시 있음) 한다. 끝나기 전에는 readiness 가 올라가지 않는다. */
@Configuration
@EnableConfigurationProperties(MapProperties.class)
public class MapGraphConfig {

  @Bean(destroyMethod = "close")
  public GraphHopper graphHopper(MapProperties props) {
    // import(처음 빌드)일 때만 near_park·near_river 를 굽는다. load(이미 구운 그래프)면 값이 저장돼 있어 생략.
    boolean fresh = !Files.exists(Path.of(props.graphLocation(), "properties"));
    GraphHopper gh = MapGraphFactory.create(props).importOrLoad();
    if (fresh) {
      AreaEncodedValues.bake(gh, props.areasDir());
    }
    return gh;
  }
}

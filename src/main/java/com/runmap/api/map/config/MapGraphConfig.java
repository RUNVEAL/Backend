package com.runmap.api.map.config;

import com.graphhopper.GraphHopper;
import com.runmap.api.map.domain.MapGraphFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 기동 시 그래프를 import(처음) 또는 load(캐시 있음) 한다. 끝나기 전에는 readiness 가 올라가지 않는다. */
@Configuration
@EnableConfigurationProperties(MapProperties.class)
public class MapGraphConfig {

  @Bean(destroyMethod = "close")
  public GraphHopper graphHopper(MapProperties props) {
    return MapGraphFactory.create(props).importOrLoad();
  }
}

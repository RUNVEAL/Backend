package com.runmap.api.route.controller;

import com.runmap.api.map.domain.MapVersions;
import com.runmap.api.route.dto.RouteRecommendRequest;
import com.runmap.api.route.dto.RouteRecommendResponse;
import com.runmap.api.route.service.RouteService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/routes")
public class RouteController {

  private final RouteService routeService;
  private final MapVersions mapVersions;

  public RouteController(RouteService routeService, MapVersions mapVersions) {
    this.routeService = routeService;
    this.mapVersions = mapVersions;
  }

  /** 코스 추천 0~3개. 좌표가 있어 POST (API 명세 §2.1). */
  @PostMapping
  public RouteRecommendResponse recommend(@RequestBody RouteRecommendRequest request) {
    request.validate();
    mapVersions.check(request.mapVersion());
    return routeService.recommend(request);
  }
}

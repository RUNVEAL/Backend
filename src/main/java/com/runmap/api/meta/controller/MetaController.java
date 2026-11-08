package com.runmap.api.meta.controller;

import static com.runmap.api.route.dto.RouteRecommendRequest.DEFAULT_DISTANCE_M;
import static com.runmap.api.route.dto.RouteRecommendRequest.DEFAULT_PACE;
import static com.runmap.api.route.dto.RouteRecommendRequest.DISTANCE_STEP_M;
import static com.runmap.api.route.dto.RouteRecommendRequest.MAX_DISTANCE_M;
import static com.runmap.api.route.dto.RouteRecommendRequest.MAX_EXPLORED;
import static com.runmap.api.route.dto.RouteRecommendRequest.MAX_PACE;
import static com.runmap.api.route.dto.RouteRecommendRequest.MIN_DISTANCE_M;
import static com.runmap.api.route.dto.RouteRecommendRequest.MIN_PACE;

import com.runmap.api.global.web.BodyLimitFilter;
import com.runmap.api.map.domain.MapVersions;
import com.runmap.api.match.dto.MatchCreateRequest;
import com.runmap.api.meta.dto.MetaResponse;
import com.runmap.api.route.domain.RouteFilter;
import com.runmap.api.route.domain.RouteTag;
import com.runmap.api.route.service.RouteService;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 서버가 실제로 탑재한 지도 버전·한도. 좌표가 없어 GET (API 명세 §3.3). 한도 값은 각 요청 DTO 상수 그대로. */
@RestController
public class MetaController {

  private final MetaResponse meta;

  public MetaController(MapVersions mapVersions, ObjectProvider<BuildProperties> build) {
    BuildProperties b = build.getIfAvailable();
    this.meta =
        new MetaResponse(
            b == null ? "dev" : b.getVersion() + "-" + b.getTime(),
            new MetaResponse.MapVersions(mapVersions.current(), mapVersions.supported()),
            new MetaResponse.Limits(
                BodyLimitFilter.MAX_BYTES,
                new MetaResponse.MatchLimits(
                    MatchCreateRequest.MIN_POINTS, MatchCreateRequest.MAX_POINTS),
                new MetaResponse.RouteLimits(
                    new MetaResponse.Range(
                        MIN_DISTANCE_M, MAX_DISTANCE_M, DISTANCE_STEP_M, DEFAULT_DISTANCE_M),
                    new MetaResponse.Range(MIN_PACE, MAX_PACE, null, DEFAULT_PACE),
                    MAX_EXPLORED,
                    RouteService.MAX_ROUTES)),
            new MetaResponse.RouteOptions(
                Arrays.stream(RouteFilter.values()).map(Enum::name).toList(),
                Arrays.stream(RouteTag.values()).map(Enum::name).toList(),
                RouteService.LOOP_TOLERANCE,
                RouteService.ONE_WAY_TOLERANCE));
  }

  @GetMapping("/api/v1/meta")
  public ResponseEntity<MetaResponse> meta() {
    return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofSeconds(60))).body(meta);
  }
}

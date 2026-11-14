package com.runmap.api.match.controller;

import com.runmap.api.global.web.ExecutionSlots;
import com.runmap.api.map.domain.MapVersions;
import com.runmap.api.match.domain.MatchPurpose;
import com.runmap.api.match.dto.MatchCreateRequest;
import com.runmap.api.match.dto.MatchResponse;
import com.runmap.api.match.service.MatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "맵매칭", description = "러닝 GPS 경로를 도로에 맞춰 달린 구간을 판정한다")
@RestController
@RequestMapping("/api/v1/match")
public class MatchController {

  private final MatchService matchService;
  private final MapVersions mapVersions;
  private final ExecutionSlots slots;

  public MatchController(MatchService matchService, MapVersions mapVersions, ExecutionSlots slots) {
    this.matchService = matchService;
    this.mapVersions = mapVersions;
    this.slots = slots;
  }

  /** 러닝 경로 → 달린 것으로 인정된 구간 ID. 저장하지 않는다. */
  @Operation(
      summary = "맵매칭 실행",
      description =
          "러닝 GPS 경로(points)를 받아 실제 도로에 스냅하고, 달린 것으로 인정된 구간(segmentId)·보정 경로·거리·시군구·행정동을"
              + " 돌려준다. 결과는 서버에 저장하지 않는다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "매칭 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "요청 형식 오류. 응답에 field·reason 포함(INVALID_REQUEST)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "지원하지 않는 mapVersion(MAP_VERSION_OUTDATED)",
        content = @Content),
    @ApiResponse(
        responseCode = "413",
        description = "점이 너무 많음(TOO_MANY_POINTS) 또는 본문 초과(PAYLOAD_TOO_LARGE)",
        content = @Content),
    @ApiResponse(
        responseCode = "422",
        description = "서비스 지역 밖(OUT_OF_SERVICE_AREA) 또는 매칭 실패(MATCH_FAILED)",
        content = @Content),
    @ApiResponse(
        responseCode = "429",
        description = "요청 한도 초과(RATE_LIMITED). Retry-After 헤더 포함",
        content = @Content),
    @ApiResponse(
        responseCode = "503",
        description = "동시 실행 한도(SERVER_BUSY). Retry-After 헤더 포함",
        content = @Content)
  })
  @PostMapping
  public MatchResponse match(@RequestBody MatchCreateRequest request) {
    request.validate();
    mapVersions.check(request.mapVersion());
    return slots.run(
        request.purpose() != MatchPurpose.LIVE,
        () -> matchService.match(request.mapVersion(), request.points()));
  }
}

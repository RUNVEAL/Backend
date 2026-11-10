package com.runmap.api.match.controller;

import com.runmap.api.global.web.ExecutionSlots;
import com.runmap.api.map.domain.MapVersions;
import com.runmap.api.match.domain.MatchPurpose;
import com.runmap.api.match.dto.MatchCreateRequest;
import com.runmap.api.match.dto.MatchResponse;
import com.runmap.api.match.service.MatchService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/matches")
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
  @PostMapping
  public MatchResponse match(@RequestBody MatchCreateRequest request) {
    request.validate();
    mapVersions.check(request.mapVersion());
    return slots.run(
        request.purpose() != MatchPurpose.LIVE,
        () -> matchService.match(request.mapVersion(), request.points()));
  }
}

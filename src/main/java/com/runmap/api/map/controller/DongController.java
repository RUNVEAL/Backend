package com.runmap.api.map.controller;

import com.runmap.api.global.error.BusinessException;
import com.runmap.api.global.error.ErrorCode;
import com.runmap.api.map.domain.AdminAreas;
import com.runmap.api.map.domain.NearbyRoads;
import com.runmap.api.map.dto.DongResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 좌표 → 정밀 행정동. 단순화 없는 원본 경계({@link AdminAreas})로 point-in-polygon 판정한다.
 *
 * <p>주인: 우선님. 경계는 지도 버전과 독립이라 mapVersion 을 받지 않는다. 조회가 가벼워 동시 실행 슬롯도 쓰지 않는다. 예외 메시지에 좌표를 넣지 않는다.
 */
@Tag(name = "행정동 조회", description = "좌표로 정밀 행정동을 찾는다")
@RestController
@RequestMapping("/api/v1/dong")
public class DongController {

  private final AdminAreas adminAreas;
  private final NearbyRoads nearbyRoads;

  public DongController(AdminAreas adminAreas, NearbyRoads nearbyRoads) {
    this.adminAreas = adminAreas;
    this.nearbyRoads = nearbyRoads;
  }

  @Operation(
      summary = "좌표 → 행정동·도로명",
      description =
          "GPS 좌표(lat·lng)를 받아 단순화 없는 원본 경계로 정밀 행정동을 돌려준다. code 는 정복률과 같은 10자리 행정동 코드다."
              + " road 는 가까운 도로명(출발·도착 표시용)이며, 근처에 이름 있는 도로가 없으면 null 이라 앱이 동네 이름으로 대체한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "lat·lng 누락 또는 값 오류. 응답에 field·reason 포함(INVALID_REQUEST)",
        content = @Content),
    @ApiResponse(
        responseCode = "422",
        description = "서비스 지역 밖(OUT_OF_SERVICE_AREA)",
        content = @Content)
  })
  @GetMapping
  public DongResponse dong(@RequestParam double lat, @RequestParam double lng) {
    validate(lat, lng);
    String code =
        adminAreas
            .codeAt(lat, lng)
            .orElseThrow(() -> new BusinessException(ErrorCode.OUT_OF_SERVICE_AREA));
    return new DongResponse(
        code,
        adminAreas.guAt(lat, lng).orElse(null),
        adminAreas.nameAt(lat, lng).orElse(null),
        nearbyRoads.nameAt(lat, lng).orElse(null));
  }

  /** 좌표 유효성. 첫 위반 1건만 던진다. 예외에 입력 좌표값을 담지 않는다. */
  private static void validate(double lat, double lng) {
    if (!Double.isFinite(lat)) {
      throw BusinessException.invalid("lat", "NOT_FINITE");
    }
    if (lat < -90 || lat > 90) {
      throw BusinessException.invalid("lat", "OUT_OF_RANGE");
    }
    if (!Double.isFinite(lng)) {
      throw BusinessException.invalid("lng", "NOT_FINITE");
    }
    if (lng < -180 || lng > 180) {
      throw BusinessException.invalid("lng", "OUT_OF_RANGE");
    }
  }
}

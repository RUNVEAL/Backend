package com.runmap.api.map.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.runmap.api.global.error.BusinessException;
import com.runmap.api.global.error.ErrorCode;
import com.runmap.api.map.dto.DongResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DongControllerTest {

  @Autowired DongController dongController;

  @Test
  void returnsDongForCoordinateInsideArea() {
    DongResponse res = dongController.dong(37.55, 126.90); // 동 A(가동) 경계 안

    assertThat(res.code()).isEqualTo("A");
    assertThat(res.gu()).isEqualTo("G1");
    assertThat(res.name()).isEqualTo("가동");
  }

  @Test
  void returnsNearbyRoadNameForOnRoadCoordinate() {
    // 서교동 fixture 도로 위 좌표(동 B 안). 가까운 이름 있는 도로가 있어야 한다.
    DongResponse res = dongController.dong(37.5552, 126.9236);

    assertThat(res.code()).isEqualTo("B");
    assertThat(res.road()).isNotBlank(); // 큰길 → 도로명 채워짐
  }

  @Test
  void roadIsNullWhenNoNamedRoadNearby() {
    // 동 A(가동) 안이지만 서교동 도로에서 멀어 근처 도로가 없다 → road 는 null(프론트가 동네명으로 대체)
    DongResponse res = dongController.dong(37.55, 126.90);

    assertThat(res.code()).isEqualTo("A");
    assertThat(res.road()).isNull();
  }

  @Test
  void throwsOutOfServiceAreaWhenOutsideAllDong() {
    // 모든 동 경계 밖(동중국해 부근)
    assertThatThrownBy(() -> dongController.dong(30.0, 120.0))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OUT_OF_SERVICE_AREA));
  }

  @Test
  void rejectsNonFiniteCoordinate() {
    assertThatThrownBy(() -> dongController.dong(Double.NaN, 126.90))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
  }

  @Test
  void rejectsOutOfRangeCoordinate() {
    // 경도 범위(-180~180) 밖
    assertThatThrownBy(() -> dongController.dong(37.55, 200.0))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
  }
}

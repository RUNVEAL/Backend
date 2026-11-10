package com.runmap.api.map.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.graphhopper.GraphHopper;
import com.runmap.api.map.domain.AdminAreas;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RegionTotalsTest {

  @Autowired GraphHopper gh;

  @Test
  void sumsRunnableLengthPerDongAndGu() {
    AdminAreas areas = new AdminAreas("src/test/resources/fixtures/admin");

    RegionTotals totals = RegionTotals.compute(gh, areas);

    assertThat(totals.byDong()).containsKey("B"); // 서교동 중심이 동 B
    double dongSum = totals.byDong().values().stream().mapToDouble(Double::doubleValue).sum();
    assertThat(totals.byGu().get("G1")).isCloseTo(dongSum, within(1.0)); // 구 = 그 구의 동 합
    assertThat(totals.byGu().get("G1")).isGreaterThan(0.0);
  }
}

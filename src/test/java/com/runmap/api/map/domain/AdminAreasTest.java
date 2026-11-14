package com.runmap.api.map.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdminAreasTest {

  private static final String DIR = "src/test/resources/fixtures/admin";

  @Test
  void resolvesDongAndGuByPoint() {
    AdminAreas areas = new AdminAreas(DIR);

    assertThat(areas.codeAt(37.5552, 126.9236)).contains("B"); // 동쪽
    assertThat(areas.codeAt(37.5552, 126.9000)).contains("A"); // 서쪽
    assertThat(areas.guAt(37.5552, 126.9236)).contains("G1");
    assertThat(areas.codeAt(0.0, 0.0)).isEmpty(); // 경계 밖
  }
}

package com.runmap.api.map.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 서울 인덱스 GeoJSON — 행정동 경계 폴리곤에 동별 총 길이({@code lengthM})를 주입해 쓴다. 경계 geometry는 admin 소스 GeoJSON을 그대로
 * 재사용(JTS 변환 없음).
 */
public final class IndexGeoJson {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private IndexGeoJson() {}

  public static void write(Path out, Path sourceDongGeoJson, MapIndex index) throws IOException {
    JsonNode root = MAPPER.readTree(Files.readString(sourceDongGeoJson));
    for (JsonNode feature : root.get("features")) {
      ObjectNode properties = (ObjectNode) feature.get("properties");
      Long lengthM = index.dongLengthM().get(properties.get("code").asString());
      if (lengthM != null) {
        properties.put("lengthM", lengthM);
      }
    }
    Files.writeString(out, MAPPER.writeValueAsString(root));
  }
}

package com.runmap.api.map.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 전국 인덱스 GeoJSON — 행정동 경계 폴리곤에 동별 총 길이({@code lengthM})를 주입한다. 경계 geometry 는 원본 그대로 통과시킨다.
 *
 * <p>경계 단순화는 여기서 하지 않는다. 동을 하나씩 따로 단순화하면 인접 동이 공유하는 경계가 제각기 줄어 서로 어긋나고(= 줌인하면 경계가 "2겹"으로 보임), 재빌드할
 * 때마다 같은 깨짐이 재발한다. 대신 공유 경계를 한 번만 줄이는 토폴로지 보존 단순화(mapshaper)를 {@code scripts/build_map.sh} 가 번들 생성
 * 뒤 downstream 단계로 수행한다. (줌인하면 경계 대신 길이가 보이므로 overview 정밀도 손실은 체감되지 않는다.)
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

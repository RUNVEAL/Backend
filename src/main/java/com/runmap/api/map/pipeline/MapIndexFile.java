package com.runmap.api.map.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import tools.jackson.databind.json.JsonMapper;

/** 서울 인덱스 총 길이를 JSON으로 저장·로딩한다. 앱이 읽는다. */
public final class MapIndexFile {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private MapIndexFile() {}

  public static void write(Path file, MapIndex index) throws IOException {
    Files.writeString(file, MAPPER.writeValueAsString(index));
  }

  public static MapIndex read(Path file) throws IOException {
    return MAPPER.readValue(Files.readString(file), MapIndex.class);
  }
}

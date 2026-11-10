package com.runmap.api.map.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import tools.jackson.databind.json.JsonMapper;

/** manifest를 JSON으로 저장·로딩한다. */
public final class ManifestFile {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private ManifestFile() {}

  public static void write(Path file, Manifest manifest) throws IOException {
    Files.writeString(file, MAPPER.writeValueAsString(manifest));
  }

  public static Manifest read(Path file) throws IOException {
    return MAPPER.readValue(Files.readString(file), Manifest.class);
  }
}

package com.runmap.api.map.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MapIndexFileTest {

  @Test
  void writesAndReadsBackIndex(@TempDir Path dir) throws IOException {
    MapIndex index = new MapIndex("2026-10-27", Map.of("A", 123L, "B", 457L), Map.of("G1", 580L));
    Path file = dir.resolve("index.json");

    MapIndexFile.write(file, index);

    assertThat(MapIndexFile.read(file)).isEqualTo(index);
  }
}

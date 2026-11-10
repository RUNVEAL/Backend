package com.runmap.api.map.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SegmentsFileTest {

  @Test
  void roundTripsEdgeToSegmentMapping(@TempDir Path dir) throws IOException {
    long[] mapping = {5L, 5L, 99L, -1L, 7_000_000_000L};
    Path file = dir.resolve("segments.bin");

    SegmentsFile.write(file, mapping);
    long[] loaded = SegmentsFile.read(file);

    assertThat(loaded).containsExactly(mapping);
  }
}

package com.runmap.api.map.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManifestFileTest {

  @Test
  void writesAndReadsManifestWithDefaults(@TempDir Path dir) throws IOException {
    Manifest manifest = Manifest.of("2026-10-27", List.of("index.json", "segments.geojson"));
    Path file = dir.resolve("manifest.json");

    ManifestFile.write(file, manifest);
    Manifest loaded = ManifestFile.read(file);

    assertThat(loaded).isEqualTo(manifest);
    assertThat(loaded.matchDistanceM()).isEqualTo(15);
    assertThat(loaded.segmentCoverage()).isEqualTo(0.60);
    assertThat(loaded.trimMinM()).isEqualTo(300);
    assertThat(loaded.files()).containsExactly("index.json", "segments.geojson");
  }
}

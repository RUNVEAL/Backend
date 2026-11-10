package com.runmap.api.map.pipeline;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * edgeId → segmentId 매핑(segments.bin)을 저장·로딩한다. A 내부 포맷 (SegmentIds만 읽음).
 *
 * <p>형식: magic("SEGS") · version · length · length개의 long(edgeId 순). 배열 인덱스 = edgeId.
 */
public final class SegmentsFile {

  private static final int MAGIC = 0x53454753; // "SEGS"
  private static final int VERSION = 1;

  private SegmentsFile() {}

  public static void write(Path file, long[] segmentIdByEdge) throws IOException {
    try (DataOutputStream out =
        new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
      out.writeInt(MAGIC);
      out.writeInt(VERSION);
      out.writeInt(segmentIdByEdge.length);
      for (long segmentId : segmentIdByEdge) {
        out.writeLong(segmentId);
      }
    }
  }

  public static long[] read(Path file) throws IOException {
    try (DataInputStream in =
        new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
      if (in.readInt() != MAGIC) {
        throw new IOException("segments.bin: bad magic");
      }
      int version = in.readInt();
      if (version != VERSION) {
        throw new IOException("segments.bin: unsupported version " + version);
      }
      long[] segmentIdByEdge = new long[in.readInt()];
      for (int i = 0; i < segmentIdByEdge.length; i++) {
        segmentIdByEdge[i] = in.readLong();
      }
      return segmentIdByEdge;
    }
  }
}

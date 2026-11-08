package com.runmap.api.global.web;

import com.runmap.api.global.error.BusinessException;
import com.runmap.api.global.error.ErrorCode;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 맵매칭·코스 추천 동시 실행 상한 (API 명세 §2.9). 자리가 2초 안에 안 나면 503 SERVER_BUSY.
 *
 * <p>bulk(IMPORT · REMATCH)는 서버 전체에서 1건만 — 러닝 직후 결과(LIVE)가 대량 처리에 밀리지 않게.
 */
@Component
public class ExecutionSlots {

  private final Semaphore slots;
  private final Semaphore bulk = new Semaphore(1);
  private final long waitMillis;

  @Autowired
  public ExecutionSlots() {
    this(defaultPermits(Runtime.getRuntime().availableProcessors()), Duration.ofSeconds(2));
  }

  // 계산은 CPU만 쓴다. 가상 스레드 캐리어(= 코어 수)를 하나 남겨야 /meta·/readyz·대기 타이머가 돈다
  static int defaultPermits(int cores) {
    return Math.max(1, cores - 1);
  }

  public ExecutionSlots(int permits, Duration wait) {
    this.slots = new Semaphore(permits);
    this.waitMillis = wait.toMillis();
  }

  public <T> T run(boolean isBulk, Supplier<T> work) {
    if (isBulk && !acquire(bulk)) {
      throw busy();
    }
    try {
      if (!acquire(slots)) {
        throw busy();
      }
      try {
        return work.get();
      } finally {
        slots.release();
      }
    } finally {
      if (isBulk) {
        bulk.release();
      }
    }
  }

  private boolean acquire(Semaphore s) {
    try {
      return s.tryAcquire(waitMillis, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private static BusinessException busy() {
    return new BusinessException(ErrorCode.SERVER_BUSY)
        .retryAfter(ThreadLocalRandom.current().nextInt(2, 6));
  }
}

package com.runmap.api.global.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.runmap.api.global.error.BusinessException;
import com.runmap.api.global.error.ErrorCode;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

class ExecutionSlotsTest {

  private static Void hold(CountDownLatch started, CountDownLatch release) {
    started.countDown();
    try {
      release.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return null;
  }

  @Test
  void rejectsWhenFullAfterWait() throws Exception {
    ExecutionSlots slots = new ExecutionSlots(1, Duration.ofMillis(100));
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CompletableFuture.runAsync(() -> slots.run(false, () -> hold(started, release)));
    started.await();
    assertThatThrownBy(() -> slots.run(false, () -> "x"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.SERVER_BUSY);
    release.countDown();
  }

  @Test
  void bulkRunsOneAtATimeEvenWithFreeSlots() throws Exception {
    ExecutionSlots slots = new ExecutionSlots(4, Duration.ofMillis(100));
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CompletableFuture.runAsync(() -> slots.run(true, () -> hold(started, release)));
    started.await();
    assertThatThrownBy(() -> slots.run(true, () -> "x")).isInstanceOf(BusinessException.class);
    assertThat(slots.run(false, () -> "live")).isEqualTo("live");
    release.countDown();
  }

  @Test
  void busyCarriesRetryAfterBetween2And5() {
    ExecutionSlots slots = new ExecutionSlots(0, Duration.ofMillis(10));
    assertThatThrownBy(() -> slots.run(false, () -> "x"))
        .isInstanceOf(BusinessException.class)
        .extracting("retryAfterSeconds")
        .satisfies(s -> assertThat((Integer) s).isBetween(2, 5));
  }
}

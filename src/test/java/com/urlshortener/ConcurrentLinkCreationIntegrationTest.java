package com.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import com.urlshortener.domain.Link;
import com.urlshortener.service.LinkService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Uniqueness under real concurrency, not mocked collisions: many threads
 * create links simultaneously against the actual unique constraint. This is
 * the test that backs the claim that constraint-plus-retry (rather than a
 * check-then-insert) is what makes creation correct.
 */
@SpringBootTest
class ConcurrentLinkCreationIntegrationTest {

  private static final int THREADS = 16;
  private static final int LINKS_PER_THREAD = 8;

  @Autowired private LinkService linkService;

  @Test
  void concurrentCreationsAllSucceedWithDistinctCodes() throws Exception {
    CountDownLatch startGate = new CountDownLatch(1);
    List<Callable<List<String>>> tasks =
        IntStream.range(0, THREADS)
            .mapToObj(
                threadIndex ->
                    (Callable<List<String>>)
                        () -> {
                          startGate.await();
                          return IntStream.range(0, LINKS_PER_THREAD)
                              .mapToObj(
                                  i ->
                                      linkService
                                          .create("https://example.com/t" + threadIndex + "/" + i)
                                          .getCode())
                              .collect(Collectors.toList());
                        })
            .collect(Collectors.toList());

    List<String> codes;
    try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
      List<Future<List<String>>> futures = tasks.stream().map(pool::submit).toList();
      startGate.countDown(); // release all threads at once to maximize contention
      codes = new ArrayList<>();
      for (Future<List<String>> future : futures) {
        codes.addAll(future.get());
      }
    }

    assertThat(codes).hasSize(THREADS * LINKS_PER_THREAD);
    assertThat(codes).doesNotHaveDuplicates();
  }

  @Test
  void concurrentlyCreatedLinksAreAllResolvable() throws Exception {
    List<String> codes;
    try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
      List<Future<String>> futures =
          IntStream.range(0, 32)
              .mapToObj(
                  i ->
                      pool.submit(
                          () -> linkService.create("https://example.com/resolve/" + i).getCode()))
              .toList();
      codes = new ArrayList<>();
      for (Future<String> future : futures) {
        codes.add(future.get());
      }
    }

    for (String code : codes) {
      Link resolved = linkService.getByCode(code);
      assertThat(resolved.getLongUrl()).startsWith("https://example.com/resolve/");
    }
  }
}

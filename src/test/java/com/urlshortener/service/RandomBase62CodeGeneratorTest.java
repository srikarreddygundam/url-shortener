package com.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.urlshortener.config.AppProperties;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RandomBase62CodeGeneratorTest {

  private final RandomBase62CodeGenerator generator =
      new RandomBase62CodeGenerator(new AppProperties("http://localhost:8080", 7, 2048));

  @Test
  void generatesCodesOfConfiguredLength() {
    assertThat(generator.newCode()).hasSize(7);
  }

  @Test
  void usesOnlyBase62Characters() {
    for (int i = 0; i < 100; i++) {
      assertThat(generator.newCode()).matches("[0-9A-Za-z]{7}");
    }
  }

  @Test
  void producesDistinctCodesAcrossManyCalls() {
    // 10k draws from a 62^7 space: a duplicate here would indicate a broken RNG,
    // not bad luck (collision probability ~1e-5).
    Set<String> codes = new HashSet<>();
    for (int i = 0; i < 10_000; i++) {
      codes.add(generator.newCode());
    }
    assertThat(codes).hasSize(10_000);
  }
}

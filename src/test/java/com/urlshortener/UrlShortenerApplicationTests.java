package com.urlshortener;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class UrlShortenerApplicationTests {

  @Test
  void applicationContextStartsWithMigrationsApplied() {
    // Fails if Flyway migrations, JPA mappings, or configuration binding are broken.
  }
}

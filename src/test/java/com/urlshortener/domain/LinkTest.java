package com.urlshortener.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class LinkTest {

  private static final Instant EXPIRY = Instant.parse("2026-12-31T00:00:00Z");

  private final Link expiringLink =
      new Link("Ab3xY9z", "https://example.com", Instant.EPOCH, EXPIRY);

  @Test
  void linkWithoutExpiryNeverExpires() {
    Link link = new Link("Ab3xY9z", "https://example.com", Instant.EPOCH);

    assertThat(link.isExpired(Instant.parse("2999-01-01T00:00:00Z"))).isFalse();
  }

  @Test
  void linkIsNotExpiredBeforeItsExpiryTime() {
    assertThat(expiringLink.isExpired(EXPIRY.minusSeconds(1))).isFalse();
  }

  @Test
  void linkIsExpiredExactlyAtItsExpiryTime() {
    assertThat(expiringLink.isExpired(EXPIRY)).isTrue();
  }

  @Test
  void linkIsExpiredAfterItsExpiryTime() {
    assertThat(expiringLink.isExpired(EXPIRY.plusSeconds(1))).isTrue();
  }
}

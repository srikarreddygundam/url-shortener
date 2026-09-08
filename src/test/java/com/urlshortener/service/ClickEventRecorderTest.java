package com.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.urlshortener.domain.ClickEvent;
import com.urlshortener.repository.ClickEventRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ClickEventRecorderTest {

  private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");

  private final ClickEventRepository clickEventRepository = mock(ClickEventRepository.class);
  private final ClickEventRecorder recorder = new ClickEventRecorder(clickEventRepository);

  @Test
  void persistsClickEventForLink() {
    recorder.onLinkClicked(new LinkClickedEvent(42L, NOW, "https://news.example.org"));

    ArgumentCaptor<ClickEvent> saved = ArgumentCaptor.forClass(ClickEvent.class);
    verify(clickEventRepository).save(saved.capture());
    assertThat(saved.getValue().getLinkId()).isEqualTo(42L);
    assertThat(saved.getValue().getOccurredAt()).isEqualTo(NOW);
    assertThat(saved.getValue().getReferrer()).isEqualTo("https://news.example.org");
  }

  @Test
  void storesNullForMissingOrBlankReferrer() {
    recorder.onLinkClicked(new LinkClickedEvent(42L, NOW, "  "));

    ArgumentCaptor<ClickEvent> saved = ArgumentCaptor.forClass(ClickEvent.class);
    verify(clickEventRepository).save(saved.capture());
    assertThat(saved.getValue().getReferrer()).isNull();
  }

  @Test
  void truncatesOversizedReferrerToColumnLimit() {
    String hugeReferrer = "https://example.com/" + "r".repeat(3000);

    recorder.onLinkClicked(new LinkClickedEvent(42L, NOW, hugeReferrer));

    ArgumentCaptor<ClickEvent> saved = ArgumentCaptor.forClass(ClickEvent.class);
    verify(clickEventRepository).save(saved.capture());
    assertThat(saved.getValue().getReferrer()).hasSize(2048);
  }

  @Test
  void swallowsStorageFailuresSoAnalyticsNeverBreaksARedirect() {
    when(clickEventRepository.save(any(ClickEvent.class)))
        .thenThrow(new RuntimeException("analytics database down"));

    assertThatCode(() -> recorder.onLinkClicked(new LinkClickedEvent(42L, NOW, null)))
        .doesNotThrowAnyException();
  }
}

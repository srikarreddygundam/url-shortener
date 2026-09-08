package com.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.urlshortener.domain.ClickEvent;
import com.urlshortener.domain.Link;
import com.urlshortener.repository.ClickEventRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ClickAnalyticsServiceTest {

  private static final Instant LAST_CLICK = Instant.parse("2026-09-08T12:34:56Z");

  private final LinkService linkService = mock(LinkService.class);
  private final ClickEventRepository clickEventRepository = mock(ClickEventRepository.class);
  private final ClickAnalyticsService service =
      new ClickAnalyticsService(linkService, clickEventRepository);

  @Test
  void aggregatesTotalClicksAndLastClickTime() {
    Link link = new Link("Ab3xY9z", "https://example.com", Instant.EPOCH);
    when(linkService.getByCode("Ab3xY9z")).thenReturn(link);
    when(clickEventRepository.countByLinkId(link.getId())).thenReturn(7L);
    when(clickEventRepository.findTopByLinkIdOrderByOccurredAtDesc(link.getId()))
        .thenReturn(Optional.of(new ClickEvent(link.getId(), LAST_CLICK, null)));

    LinkStats stats = service.statsFor("Ab3xY9z");

    assertThat(stats.code()).isEqualTo("Ab3xY9z");
    assertThat(stats.totalClicks()).isEqualTo(7);
    assertThat(stats.lastClickAt()).isEqualTo(LAST_CLICK);
  }

  @Test
  void reportsZeroClicksAndNoLastClickForUnvisitedLink() {
    Link link = new Link("Ab3xY9z", "https://example.com", Instant.EPOCH);
    when(linkService.getByCode("Ab3xY9z")).thenReturn(link);
    when(clickEventRepository.countByLinkId(link.getId())).thenReturn(0L);
    when(clickEventRepository.findTopByLinkIdOrderByOccurredAtDesc(link.getId()))
        .thenReturn(Optional.empty());

    LinkStats stats = service.statsFor("Ab3xY9z");

    assertThat(stats.totalClicks()).isZero();
    assertThat(stats.lastClickAt()).isNull();
  }

  @Test
  void propagatesNotFoundForUnknownCode() {
    when(linkService.getByCode("missing1")).thenThrow(new LinkNotFoundException("missing1"));

    assertThatThrownBy(() -> service.statsFor("missing1"))
        .isInstanceOf(LinkNotFoundException.class);
  }
}

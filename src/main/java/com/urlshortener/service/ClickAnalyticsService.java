package com.urlshortener.service;

import com.urlshortener.domain.ClickEvent;
import com.urlshortener.domain.Link;
import com.urlshortener.repository.ClickEventRepository;
import org.springframework.stereotype.Service;

/**
 * All interpretation of "analytics" lives here, so a different product
 * answer (unique visitors, time buckets, ...) changes this class and its
 * queries — not the redirect path.
 */
@Service
public class ClickAnalyticsService {

  private final LinkService linkService;
  private final ClickEventRepository clickEventRepository;

  public ClickAnalyticsService(LinkService linkService, ClickEventRepository clickEventRepository) {
    this.linkService = linkService;
    this.clickEventRepository = clickEventRepository;
  }

  public LinkStats statsFor(String code) {
    Link link = linkService.getByCode(code);
    long totalClicks = clickEventRepository.countByLinkId(link.getId());
    return new LinkStats(
        link.getCode(),
        totalClicks,
        clickEventRepository
            .findTopByLinkIdOrderByOccurredAtDesc(link.getId())
            .map(ClickEvent::getOccurredAt)
            .orElse(null));
  }
}

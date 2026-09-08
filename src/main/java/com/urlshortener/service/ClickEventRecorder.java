package com.urlshortener.service;

import com.urlshortener.domain.ClickEvent;
import com.urlshortener.repository.ClickEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Persists click events off the request thread. Failures are logged and
 * swallowed on purpose: losing an analytics data point must never surface to
 * the user following a redirect.
 */
@Component
public class ClickEventRecorder {

  private static final Logger log = LoggerFactory.getLogger(ClickEventRecorder.class);
  private static final int MAX_REFERRER_LENGTH = 2048;

  private final ClickEventRepository clickEventRepository;

  public ClickEventRecorder(ClickEventRepository clickEventRepository) {
    this.clickEventRepository = clickEventRepository;
  }

  @Async
  @EventListener
  public void onLinkClicked(LinkClickedEvent event) {
    try {
      clickEventRepository.save(
          new ClickEvent(event.linkId(), event.occurredAt(), normalizeReferrer(event.referrer())));
    } catch (RuntimeException e) {
      log.error("Failed to record click for link id {}", event.linkId(), e);
    }
  }

  private String normalizeReferrer(String referrer) {
    if (referrer == null || referrer.isBlank()) {
      return null;
    }
    // Client-supplied header: cap it to the column size instead of trusting it.
    return referrer.length() <= MAX_REFERRER_LENGTH
        ? referrer
        : referrer.substring(0, MAX_REFERRER_LENGTH);
  }
}

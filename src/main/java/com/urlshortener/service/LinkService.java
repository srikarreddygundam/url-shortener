package com.urlshortener.service;

import com.urlshortener.domain.Link;
import com.urlshortener.repository.LinkRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class LinkService {

  private static final Logger log = LoggerFactory.getLogger(LinkService.class);
  private static final int MAX_CODE_ATTEMPTS = 5;

  private final LinkRepository linkRepository;
  private final CodeGenerator codeGenerator;
  private final UrlValidator urlValidator;
  private final Clock clock;
  private final ApplicationEventPublisher eventPublisher;

  public LinkService(
      LinkRepository linkRepository,
      CodeGenerator codeGenerator,
      UrlValidator urlValidator,
      Clock clock,
      ApplicationEventPublisher eventPublisher) {
    this.linkRepository = linkRepository;
    this.codeGenerator = codeGenerator;
    this.urlValidator = urlValidator;
    this.clock = clock;
    this.eventPublisher = eventPublisher;
  }

  /**
   * Uniqueness is enforced by the database constraint, not by a
   * check-then-insert (which would race under concurrent requests). On the
   * rare collision we retry with a fresh code.
   *
   * <p>Deliberately not annotated @Transactional: each saveAndFlush runs in
   * its own transaction, so a constraint violation doesn't mark an outer
   * transaction rollback-only and break the retry.
   */
  public Link create(String rawUrl) {
    String longUrl = urlValidator.validate(rawUrl);
    for (int attempt = 1; attempt <= MAX_CODE_ATTEMPTS; attempt++) {
      String code = codeGenerator.newCode();
      try {
        return linkRepository.saveAndFlush(new Link(code, longUrl, clock.instant()));
      } catch (DataIntegrityViolationException e) {
        log.warn("Short code collision, attempt {}/{}", attempt, MAX_CODE_ATTEMPTS);
      }
    }
    throw new CodeGenerationException(
        "Could not generate a unique short code after " + MAX_CODE_ATTEMPTS + " attempts");
  }

  public Link getByCode(String code) {
    return linkRepository
        .findByCode(code)
        .orElseThrow(() -> new LinkNotFoundException(code));
  }

  /**
   * Resolves a code for the redirect path and emits a click event. Event
   * publication is guarded: analytics must never break or slow a redirect.
   */
  public Link resolveForRedirect(String code, String referrer) {
    Link link = getByCode(code);
    try {
      eventPublisher.publishEvent(new LinkClickedEvent(link.getId(), clock.instant(), referrer));
    } catch (RuntimeException e) {
      log.error("Failed to publish click event for code {}", code, e);
    }
    return link;
  }
}

package com.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.urlshortener.config.AppProperties;
import com.urlshortener.domain.Link;
import com.urlshortener.repository.LinkRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

class LinkServiceTest {

  private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");

  private final LinkRepository linkRepository = mock(LinkRepository.class);
  private final CodeGenerator codeGenerator = mock(CodeGenerator.class);
  private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
  private final UrlValidator urlValidator =
      new UrlValidator(new AppProperties("http://localhost:8080", 7, 2048));

  private final LinkService service =
      new LinkService(
          linkRepository,
          codeGenerator,
          urlValidator,
          Clock.fixed(NOW, ZoneOffset.UTC),
          eventPublisher);

  @Test
  void createSavesLinkWithGeneratedCodeAndTimestamp() {
    when(codeGenerator.newCode()).thenReturn("Ab3xY9z");
    when(linkRepository.saveAndFlush(any(Link.class))).thenAnswer(inv -> inv.getArgument(0));

    Link link = service.create("https://example.com/docs");

    ArgumentCaptor<Link> saved = ArgumentCaptor.forClass(Link.class);
    verify(linkRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getCode()).isEqualTo("Ab3xY9z");
    assertThat(saved.getValue().getLongUrl()).isEqualTo("https://example.com/docs");
    assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
    assertThat(link).isSameAs(saved.getValue());
  }

  @Test
  void createRetriesWithFreshCodeWhenCodeCollides() {
    when(codeGenerator.newCode()).thenReturn("collide", "fresh12");
    when(linkRepository.saveAndFlush(any(Link.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate key: uq_links_code"))
        .thenAnswer(inv -> inv.getArgument(0));

    Link link = service.create("https://example.com");

    assertThat(link.getCode()).isEqualTo("fresh12");
    verify(linkRepository, times(2)).saveAndFlush(any(Link.class));
  }

  @Test
  void createGivesUpAfterExhaustingCollisionRetries() {
    when(codeGenerator.newCode()).thenReturn("collide");
    when(linkRepository.saveAndFlush(any(Link.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate key: uq_links_code"));

    assertThatThrownBy(() -> service.create("https://example.com"))
        .isInstanceOf(CodeGenerationException.class);
    verify(linkRepository, times(5)).saveAndFlush(any(Link.class));
  }

  @Test
  void createRejectsInvalidUrlBeforeTouchingTheDatabase() {
    assertThatThrownBy(() -> service.create("javascript:alert(1)"))
        .isInstanceOf(InvalidUrlException.class);
    verifyNoInteractions(linkRepository, codeGenerator);
  }

  @Test
  void getByCodeThrowsNotFoundForUnknownCode() {
    when(linkRepository.findByCode("missing1")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getByCode("missing1"))
        .isInstanceOf(LinkNotFoundException.class)
        .hasMessageContaining("missing1");
  }

  @Test
  void createStoresFutureExpirationWhenProvided() {
    when(codeGenerator.newCode()).thenReturn("Ab3xY9z");
    when(linkRepository.saveAndFlush(any(Link.class))).thenAnswer(inv -> inv.getArgument(0));

    Link link = service.create("https://example.com", NOW.plusSeconds(3600));

    assertThat(link.getExpiresAt()).isEqualTo(NOW.plusSeconds(3600));
  }

  @Test
  void createRejectsExpirationInThePastBeforeTouchingTheDatabase() {
    assertThatThrownBy(() -> service.create("https://example.com", NOW.minusSeconds(1)))
        .isInstanceOf(InvalidExpirationException.class);
    verifyNoInteractions(linkRepository, codeGenerator);
  }

  @Test
  void createRejectsExpirationExactlyAtCreationTime() {
    assertThatThrownBy(() -> service.create("https://example.com", NOW))
        .isInstanceOf(InvalidExpirationException.class);
    verifyNoInteractions(linkRepository, codeGenerator);
  }

  @Test
  void resolveForRedirect_refusesExpiredLinkAndRecordsNoClick() {
    Link expired =
        new Link("Ab3xY9z", "https://example.com", NOW.minusSeconds(7200), NOW.minusSeconds(1));
    when(linkRepository.findByCode("Ab3xY9z")).thenReturn(Optional.of(expired));

    assertThatThrownBy(() -> service.resolveForRedirect("Ab3xY9z", null))
        .isInstanceOf(LinkExpiredException.class);
    verifyNoInteractions(eventPublisher);
  }

  @Test
  void resolveForRedirect_allowsLinkWhoseExpiryIsStillInTheFuture() {
    Link notYetExpired =
        new Link("Ab3xY9z", "https://example.com", NOW.minusSeconds(60), NOW.plusSeconds(60));
    when(linkRepository.findByCode("Ab3xY9z")).thenReturn(Optional.of(notYetExpired));

    Link resolved = service.resolveForRedirect("Ab3xY9z", null);

    assertThat(resolved).isSameAs(notYetExpired);
  }

  @Test
  void resolveForRedirect_publishesClickEventWithLinkIdAndReferrer() {
    Link link = new Link("Ab3xY9z", "https://example.com", NOW);
    when(linkRepository.findByCode("Ab3xY9z")).thenReturn(Optional.of(link));

    service.resolveForRedirect("Ab3xY9z", "https://twitter.com/somepost");

    ArgumentCaptor<LinkClickedEvent> published = ArgumentCaptor.forClass(LinkClickedEvent.class);
    verify(eventPublisher).publishEvent(published.capture());
    assertThat(published.getValue().linkId()).isEqualTo(link.getId());
    assertThat(published.getValue().occurredAt()).isEqualTo(NOW);
    assertThat(published.getValue().referrer()).isEqualTo("https://twitter.com/somepost");
  }

  @Test
  void resolveForRedirect_stillReturnsLinkWhenEventPublicationFails() {
    Link link = new Link("Ab3xY9z", "https://example.com", NOW);
    when(linkRepository.findByCode("Ab3xY9z")).thenReturn(Optional.of(link));
    doThrow(new IllegalStateException("event infrastructure down"))
        .when(eventPublisher)
        .publishEvent(any(Object.class));

    Link resolved = service.resolveForRedirect("Ab3xY9z", null);

    assertThat(resolved).isSameAs(link);
  }
}

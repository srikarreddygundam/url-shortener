package com.urlshortener.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Holds the link id rather than a Link association: analytics writes need no
 * object graph, and keeping the entity flat keeps the async insert cheap.
 */
@Entity
@Table(name = "click_events")
public class ClickEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "link_id", nullable = false)
  private Long linkId;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(length = 2048)
  private String referrer;

  protected ClickEvent() {
    // required by JPA
  }

  public ClickEvent(Long linkId, Instant occurredAt, String referrer) {
    this.linkId = linkId;
    this.occurredAt = occurredAt;
    this.referrer = referrer;
  }

  public Long getId() {
    return id;
  }

  public Long getLinkId() {
    return linkId;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public String getReferrer() {
    return referrer;
  }
}

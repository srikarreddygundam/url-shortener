package com.urlshortener.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "links")
public class Link {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 16)
  private String code;

  @Column(name = "long_url", nullable = false, length = 2048)
  private String longUrl;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "expires_at")
  private Instant expiresAt;

  protected Link() {
    // required by JPA
  }

  public Link(String code, String longUrl, Instant createdAt) {
    this(code, longUrl, createdAt, null);
  }

  public Link(String code, String longUrl, Instant createdAt, Instant expiresAt) {
    this.code = code;
    this.longUrl = longUrl;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }

  public Long getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getLongUrl() {
    return longUrl;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  /** A link expires the moment "now" reaches expiresAt; NULL never expires. */
  public boolean isExpired(Instant now) {
    return expiresAt != null && !now.isBefore(expiresAt);
  }
}

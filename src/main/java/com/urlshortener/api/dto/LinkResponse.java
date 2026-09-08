package com.urlshortener.api.dto;

import com.urlshortener.domain.Link;
import java.time.Instant;

public record LinkResponse(
    String code, String shortUrl, String longUrl, Instant createdAt, Instant expiresAt) {

  public static LinkResponse from(Link link, String baseUrl) {
    return new LinkResponse(
        link.getCode(),
        baseUrl + "/" + link.getCode(),
        link.getLongUrl(),
        link.getCreatedAt(),
        link.getExpiresAt());
  }
}

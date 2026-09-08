package com.urlshortener.service;

import com.urlshortener.config.AppProperties;
import java.net.URI;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Validates submitted long URLs by parsing, not by regex. Scheme is
 * allowlisted: accepting arbitrary schemes would let this service redirect
 * users into javascript:, file:, or other dangerous handlers.
 */
@Component
public class UrlValidator {

  private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

  private final int maxUrlLength;

  public UrlValidator(AppProperties properties) {
    this.maxUrlLength = properties.maxUrlLength();
  }

  /**
   * Returns the URL to store, or throws {@link InvalidUrlException}. The URL is
   * stored as submitted (whitespace-stripped only): canonicalizing case or
   * trailing slashes can change meaning on some target servers.
   */
  public String validate(String rawUrl) {
    if (rawUrl == null || rawUrl.isBlank()) {
      throw new InvalidUrlException("URL must not be blank");
    }
    String url = rawUrl.strip();
    if (url.length() > maxUrlLength) {
      throw new InvalidUrlException("URL exceeds the maximum length of " + maxUrlLength);
    }
    URI uri = parse(url);
    if (!uri.isAbsolute()) {
      throw new InvalidUrlException("URL must be absolute, including http:// or https://");
    }
    if (!ALLOWED_SCHEMES.contains(uri.getScheme().toLowerCase())) {
      throw new InvalidUrlException("Only http and https URLs are supported");
    }
    if (uri.getHost() == null || uri.getHost().isBlank()) {
      throw new InvalidUrlException("URL must include a host");
    }
    return url;
  }

  private URI parse(String url) {
    try {
      return new URI(url);
    } catch (java.net.URISyntaxException e) {
      throw new InvalidUrlException("URL is not well-formed");
    }
  }
}

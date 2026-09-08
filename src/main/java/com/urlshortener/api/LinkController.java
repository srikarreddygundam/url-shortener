package com.urlshortener.api;

import com.urlshortener.api.dto.CreateLinkRequest;
import com.urlshortener.api.dto.LinkResponse;
import com.urlshortener.api.dto.LinkStatsResponse;
import com.urlshortener.config.AppProperties;
import com.urlshortener.domain.Link;
import com.urlshortener.service.ClickAnalyticsService;
import com.urlshortener.service.LinkService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/links")
public class LinkController {

  private final LinkService linkService;
  private final ClickAnalyticsService clickAnalyticsService;
  private final AppProperties properties;

  public LinkController(
      LinkService linkService,
      ClickAnalyticsService clickAnalyticsService,
      AppProperties properties) {
    this.linkService = linkService;
    this.clickAnalyticsService = clickAnalyticsService;
    this.properties = properties;
  }

  @PostMapping
  public ResponseEntity<LinkResponse> create(@Valid @RequestBody CreateLinkRequest request) {
    Link link = linkService.create(request.url(), request.expiresAt());
    LinkResponse response = LinkResponse.from(link, properties.baseUrl());
    return ResponseEntity.created(URI.create(response.shortUrl())).body(response);
  }

  @GetMapping("/{code:[0-9A-Za-z]+}")
  public LinkResponse getByCode(@PathVariable String code) {
    return LinkResponse.from(linkService.getByCode(code), properties.baseUrl());
  }

  @GetMapping("/{code:[0-9A-Za-z]+}/stats")
  public LinkStatsResponse getStats(@PathVariable String code) {
    return LinkStatsResponse.from(clickAnalyticsService.statsFor(code));
  }
}

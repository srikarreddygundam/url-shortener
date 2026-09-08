package com.urlshortener.api;

import com.urlshortener.domain.Link;
import com.urlshortener.service.LinkService;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@Controller
public class RedirectController {

  private final LinkService linkService;

  public RedirectController(LinkService linkService) {
    this.linkService = linkService;
  }

  /**
   * 302 rather than 301: a permanent redirect gets cached by browsers and
   * intermediaries, after which repeat visits never reach us — killing click
   * analytics and any future ability to disable a link.
   *
   * <p>The path regex keeps this catch-all mapping from swallowing arbitrary
   * paths; literal mappings like /actuator take precedence regardless.
   */
  @GetMapping("/{code:[0-9A-Za-z]+}")
  public ResponseEntity<Void> redirect(
      @PathVariable String code,
      @RequestHeader(value = HttpHeaders.REFERER, required = false) String referrer) {
    Link link = linkService.resolveForRedirect(code, referrer);
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(link.getLongUrl()))
        .build();
  }
}

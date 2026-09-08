package com.urlshortener.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.urlshortener.config.AppProperties;
import com.urlshortener.domain.Link;
import com.urlshortener.service.ClickAnalyticsService;
import com.urlshortener.service.InvalidUrlException;
import com.urlshortener.service.LinkNotFoundException;
import com.urlshortener.service.LinkService;
import com.urlshortener.service.LinkStats;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LinkController.class)
@EnableConfigurationProperties(AppProperties.class)
class LinkApiTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private LinkService linkService;
  @MockitoBean private ClickAnalyticsService clickAnalyticsService;

  @Test
  void createLink_forValidUrl_returns201WithShortUrl() throws Exception {
    when(linkService.create("https://example.com/docs", null))
        .thenReturn(new Link("Ab3xY9z", "https://example.com/docs", Instant.parse("2026-09-08T12:00:00Z")));

    mvc.perform(
            post("/api/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com/docs\"}"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "http://localhost:8080/Ab3xY9z"))
        .andExpect(jsonPath("$.code").value("Ab3xY9z"))
        .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/Ab3xY9z"))
        .andExpect(jsonPath("$.longUrl").value("https://example.com/docs"));
  }

  @Test
  void createLink_withMissingUrlField_returns400WithoutCallingService() throws Exception {
    mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(linkService);
  }

  @Test
  void createLink_withFutureExpiry_echoesExpiresAtInResponse() throws Exception {
    Instant expiry = Instant.parse("2026-12-31T00:00:00Z");
    when(linkService.create("https://example.com/campaign", expiry))
        .thenReturn(
            new Link(
                "Xp1ry77",
                "https://example.com/campaign",
                Instant.parse("2026-09-08T12:00:00Z"),
                expiry));

    mvc.perform(
            post("/api/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"url\":\"https://example.com/campaign\","
                        + "\"expiresAt\":\"2026-12-31T00:00:00Z\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.expiresAt").value("2026-12-31T00:00:00Z"));
  }

  @Test
  void createLink_forRejectedUrl_returns400ProblemDetail() throws Exception {
    when(linkService.create(anyString(), any()))
        .thenThrow(new InvalidUrlException("Only http and https URLs are supported"));

    mvc.perform(
            post("/api/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"ftp://host/file\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Only http and https URLs are supported"));
  }

  @Test
  void getLink_forKnownCode_returnsDetails() throws Exception {
    when(linkService.getByCode("Ab3xY9z"))
        .thenReturn(new Link("Ab3xY9z", "https://example.com/docs", Instant.parse("2026-09-08T12:00:00Z")));

    mvc.perform(get("/api/links/Ab3xY9z"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.longUrl").value("https://example.com/docs"));
  }

  @Test
  void getLink_forUnknownCode_returns404ProblemDetail() throws Exception {
    when(linkService.getByCode("missing1")).thenThrow(new LinkNotFoundException("missing1"));

    mvc.perform(get("/api/links/missing1"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("No link found for code 'missing1'"));
  }

  @Test
  void getStats_forKnownCode_returnsClickTotals() throws Exception {
    when(clickAnalyticsService.statsFor("Ab3xY9z"))
        .thenReturn(new LinkStats("Ab3xY9z", 7, Instant.parse("2026-09-08T12:34:56Z")));

    mvc.perform(get("/api/links/Ab3xY9z/stats"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("Ab3xY9z"))
        .andExpect(jsonPath("$.totalClicks").value(7))
        .andExpect(jsonPath("$.lastClickAt").value("2026-09-08T12:34:56Z"));
  }

  @Test
  void getStats_forUnknownCode_returns404ProblemDetail() throws Exception {
    when(clickAnalyticsService.statsFor("missing1"))
        .thenThrow(new LinkNotFoundException("missing1"));

    mvc.perform(get("/api/links/missing1/stats")).andExpect(status().isNotFound());
  }
}

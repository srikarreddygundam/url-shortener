package com.urlshortener.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.urlshortener.config.AppProperties;
import com.urlshortener.service.ClickAnalyticsService;
import com.urlshortener.service.LinkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The error contract as clients experience it: framework-mapped statuses
 * survive the catch-all handler, and unexpected failures return a generic
 * message rather than leaking internals.
 */
@WebMvcTest(LinkController.class)
@EnableConfigurationProperties(AppProperties.class)
class ErrorContractApiTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private LinkService linkService;
  @MockitoBean private ClickAnalyticsService clickAnalyticsService;

  @Test
  void malformedJsonBody_returns400NotA500() throws Exception {
    mvc.perform(
            post("/api/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\": \"https://example.com"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void unsupportedHttpMethod_returns405NotA500() throws Exception {
    mvc.perform(delete("/api/links/Ab3xY9z")).andExpect(status().isMethodNotAllowed());
  }

  @Test
  void unexpectedServiceFailure_returns500WithoutLeakingInternals() throws Exception {
    when(linkService.create(anyString(), any()))
        .thenThrow(new IllegalStateException("connection to db-prod-1 refused"));

    String body =
        mvc.perform(
                post("/api/links")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"url\":\"https://example.com/docs\"}"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body).doesNotContain("db-prod-1");
  }
}

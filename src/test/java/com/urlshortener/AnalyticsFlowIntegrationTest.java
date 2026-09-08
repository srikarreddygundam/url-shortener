package com.urlshortener;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises the real asynchronous analytics path: redirects publish events,
 * an @Async listener persists them, and the stats endpoint aggregates. The
 * Awaitility wait is the point — we test the async hop, not a synchronous
 * stand-in.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AnalyticsFlowIntegrationTest {

  @Autowired private MockMvc mvc;

  @Test
  void clicksOnAShortLink_showUpInItsStats() throws Exception {
    String body =
        mvc.perform(
                post("/api/links")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"url\":\"https://example.com/analytics-target\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String code = JsonPath.read(body, "$.code");

    mvc.perform(get("/" + code)).andExpect(status().isFound());
    mvc.perform(get("/" + code).header("Referer", "https://news.example.org"))
        .andExpect(status().isFound());

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                mvc.perform(get("/api/links/" + code + "/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalClicks").value(2)));
  }

  @Test
  void statsForAFreshLink_reportZeroClicksAndNoLastClick() throws Exception {
    String body =
        mvc.perform(
                post("/api/links")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"url\":\"https://example.com/unvisited\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String code = JsonPath.read(body, "$.code");

    mvc.perform(get("/api/links/" + code + "/stats"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalClicks").value(0))
        .andExpect(jsonPath("$.lastClickAt").isEmpty());
  }
}

package com.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full-stack flow through real wiring: HTTP -> service -> JPA -> H2 schema
 * created by Flyway. Complements the mocked web-slice tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UrlShortenerFlowIntegrationTest {

  @Autowired private MockMvc mvc;

  @Test
  void shortenThenFollowRedirect_endToEnd() throws Exception {
    String body =
        mvc.perform(
                post("/api/links")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"url\":\"https://example.com/some/long/path?q=1\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String code = JsonPath.read(body, "$.code");

    mvc.perform(get("/" + code))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://example.com/some/long/path?q=1"));

    mvc.perform(get("/api/links/" + code))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.longUrl").value("https://example.com/some/long/path?q=1"));
  }

  @Test
  void submittingTheSameUrlTwice_createsIndependentLinks() throws Exception {
    String request = "{\"url\":\"https://example.com/duplicate\"}";

    String first = shorten(request);
    String second = shorten(request);

    assertThat(first).isNotEqualTo(second);
  }

  private String shorten(String request) throws Exception {
    String body =
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.code");
  }
}

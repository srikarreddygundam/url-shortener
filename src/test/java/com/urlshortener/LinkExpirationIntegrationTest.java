package com.urlshortener;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * New behavior plus regression: links with a future expiry work today, past
 * expiry is rejected, and links without an expiry behave exactly as before
 * the change (the pre-existing flow tests cover that path too).
 */
@SpringBootTest
@AutoConfigureMockMvc
class LinkExpirationIntegrationTest {

  @Autowired private MockMvc mvc;

  @Test
  void creatingALinkWithPastExpiry_isRejectedWith400() throws Exception {
    mvc.perform(
            post("/api/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"url\":\"https://example.com/old-campaign\","
                        + "\"expiresAt\":\"2020-01-01T00:00:00Z\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("expiresAt must be in the future"));
  }

  @Test
  void linkWithFutureExpiry_redirectsNormallyUntilItExpires() throws Exception {
    String body =
        mvc.perform(
                post("/api/links")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"url\":\"https://example.com/campaign\","
                            + "\"expiresAt\":\"2099-01-01T00:00:00Z\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.expiresAt").value("2099-01-01T00:00:00Z"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String code = JsonPath.read(body, "$.code");

    mvc.perform(get("/" + code)).andExpect(status().isFound());
  }

  @Test
  void linkWithoutExpiry_stillWorksExactlyAsBefore() throws Exception {
    String body =
        mvc.perform(
                post("/api/links")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"url\":\"https://example.com/evergreen\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.expiresAt").isEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String code = JsonPath.read(body, "$.code");

    mvc.perform(get("/" + code)).andExpect(status().isFound());
  }
}

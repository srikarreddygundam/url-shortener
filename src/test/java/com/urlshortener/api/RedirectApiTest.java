package com.urlshortener.api;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.urlshortener.domain.Link;
import com.urlshortener.service.LinkNotFoundException;
import com.urlshortener.service.LinkService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RedirectController.class)
class RedirectApiTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private LinkService linkService;

  @Test
  void redirect_forKnownCode_returns302ToTheLongUrl() throws Exception {
    when(linkService.getByCode("Ab3xY9z"))
        .thenReturn(new Link("Ab3xY9z", "https://example.com/docs", Instant.now()));

    mvc.perform(get("/Ab3xY9z"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://example.com/docs"));
  }

  @Test
  void redirect_forUnknownCode_returns404() throws Exception {
    when(linkService.getByCode("missing1")).thenThrow(new LinkNotFoundException("missing1"));

    mvc.perform(get("/missing1")).andExpect(status().isNotFound());
  }

  @Test
  void redirect_forPathWithDisallowedCharacters_returns404WithoutLookup() throws Exception {
    mvc.perform(get("/abc_def")).andExpect(status().isNotFound());

    verifyNoInteractions(linkService);
  }
}

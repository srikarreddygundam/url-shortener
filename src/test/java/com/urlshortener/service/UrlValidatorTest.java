package com.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.urlshortener.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UrlValidatorTest {

  private final UrlValidator validator =
      new UrlValidator(new AppProperties("http://localhost:8080", 7, 2048));

  @Test
  void acceptsWellFormedHttpsUrl() {
    assertThat(validator.validate("https://example.com/docs?page=2"))
        .isEqualTo("https://example.com/docs?page=2");
  }

  @Test
  void acceptsHttpUrl() {
    assertThat(validator.validate("http://example.com")).isEqualTo("http://example.com");
  }

  @Test
  void stripsSurroundingWhitespaceButPreservesTheUrl() {
    assertThat(validator.validate("  https://example.com/docs \n"))
        .isEqualTo("https://example.com/docs");
  }

  @ParameterizedTest
  @ValueSource(strings = {"javascript:alert(1)", "ftp://host/file", "file:///etc/passwd", "data:text/html,hi"})
  void rejectsDangerousOrUnsupportedSchemes(String url) {
    assertThatThrownBy(() -> validator.validate(url))
        .isInstanceOf(InvalidUrlException.class)
        .hasMessageContaining("http and https");
  }

  @Test
  void rejectsRelativeUrlWithoutScheme() {
    assertThatThrownBy(() -> validator.validate("example.com/path"))
        .isInstanceOf(InvalidUrlException.class)
        .hasMessageContaining("absolute");
  }

  @Test
  void rejectsUrlWithoutHost() {
    assertThatThrownBy(() -> validator.validate("http:///just-a-path"))
        .isInstanceOf(InvalidUrlException.class)
        .hasMessageContaining("host");
  }

  @Test
  void rejectsMalformedUrl() {
    assertThatThrownBy(() -> validator.validate("http://exa mple.com"))
        .isInstanceOf(InvalidUrlException.class)
        .hasMessageContaining("well-formed");
  }

  @Test
  void rejectsBlankUrl() {
    assertThatThrownBy(() -> validator.validate("   "))
        .isInstanceOf(InvalidUrlException.class)
        .hasMessageContaining("blank");
  }

  @Test
  void rejectsUrlOverMaximumLength() {
    String url = "https://example.com/" + "a".repeat(2048);
    assertThatThrownBy(() -> validator.validate(url))
        .isInstanceOf(InvalidUrlException.class)
        .hasMessageContaining("maximum length");
  }
}

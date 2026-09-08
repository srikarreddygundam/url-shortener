package com.urlshortener.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateLinkRequest(
    @NotBlank(message = "url is required")
        @Size(max = 2048, message = "url exceeds the maximum length of 2048")
        String url,
    Instant expiresAt) {}

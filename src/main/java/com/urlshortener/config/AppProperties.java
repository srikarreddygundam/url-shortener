package com.urlshortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application tunables live here rather than as scattered literals so an
 * environment can change them without a code change.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(String baseUrl, int codeLength, int maxUrlLength) {}

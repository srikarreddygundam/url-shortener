package com.urlshortener.service;

import java.time.Instant;

public record LinkStats(String code, long totalClicks, Instant lastClickAt) {}

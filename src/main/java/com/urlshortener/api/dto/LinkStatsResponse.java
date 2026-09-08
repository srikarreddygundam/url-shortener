package com.urlshortener.api.dto;

import com.urlshortener.service.LinkStats;
import java.time.Instant;

public record LinkStatsResponse(String code, long totalClicks, Instant lastClickAt) {

  public static LinkStatsResponse from(LinkStats stats) {
    return new LinkStatsResponse(stats.code(), stats.totalClicks(), stats.lastClickAt());
  }
}

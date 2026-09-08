package com.urlshortener.service;

import java.time.Instant;

/**
 * The seam between the redirect path and analytics. At higher scale this is
 * where a message broker replaces the in-process event without touching the
 * redirect flow.
 */
public record LinkClickedEvent(Long linkId, Instant occurredAt, String referrer) {}

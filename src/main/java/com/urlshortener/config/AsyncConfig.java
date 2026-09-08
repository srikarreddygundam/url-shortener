package com.urlshortener.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables @Async so click recording runs off the redirect request thread,
 * on Boot's auto-configured application task executor.
 */
@Configuration
@EnableAsync
public class AsyncConfig {}

package com.urlshortener.service;

/**
 * Seam for short-code generation. The prototype uses random base62; at higher
 * scale this is where a pre-allocated key range or ID-encoding strategy would
 * plug in without touching LinkService.
 */
public interface CodeGenerator {

  String newCode();
}

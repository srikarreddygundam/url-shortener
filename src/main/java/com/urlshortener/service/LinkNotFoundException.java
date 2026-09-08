package com.urlshortener.service;

public class LinkNotFoundException extends RuntimeException {

  public LinkNotFoundException(String code) {
    super("No link found for code '" + code + "'");
  }
}

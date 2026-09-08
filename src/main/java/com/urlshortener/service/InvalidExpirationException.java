package com.urlshortener.service;

public class InvalidExpirationException extends RuntimeException {

  public InvalidExpirationException(String message) {
    super(message);
  }
}

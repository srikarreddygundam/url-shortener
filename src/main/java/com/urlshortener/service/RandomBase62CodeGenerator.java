package com.urlshortener.service;

import com.urlshortener.config.AppProperties;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Random base62 codes. SecureRandom (not ThreadLocalRandom) so codes are not
 * predictable — sequential or guessable codes would let anyone enumerate every
 * shortened link in the system.
 */
@Component
public class RandomBase62CodeGenerator implements CodeGenerator {

  private static final String ALPHABET =
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

  private final SecureRandom random = new SecureRandom();
  private final int codeLength;

  public RandomBase62CodeGenerator(AppProperties properties) {
    this.codeLength = properties.codeLength();
  }

  @Override
  public String newCode() {
    StringBuilder code = new StringBuilder(codeLength);
    for (int i = 0; i < codeLength; i++) {
      code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
    }
    return code.toString();
  }
}

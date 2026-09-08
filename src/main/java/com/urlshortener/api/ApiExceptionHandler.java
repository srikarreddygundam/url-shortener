package com.urlshortener.api;

import com.urlshortener.service.CodeGenerationException;
import com.urlshortener.service.InvalidExpirationException;
import com.urlshortener.service.InvalidUrlException;
import com.urlshortener.service.LinkExpiredException;
import com.urlshortener.service.LinkNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Central place turning domain exceptions into RFC 7807 responses, so
 * controllers stay free of error-shaping and clients get one error format.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(InvalidUrlException.class)
  public ProblemDetail handleInvalidUrl(InvalidUrlException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(InvalidExpirationException.class)
  public ProblemDetail handleInvalidExpiration(InvalidExpirationException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(LinkNotFoundException.class)
  public ProblemDetail handleNotFound(LinkNotFoundException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  /** 410, not 404: the link existed and is deliberately gone. */
  @ExceptionHandler(LinkExpiredException.class)
  public ProblemDetail handleExpired(LinkExpiredException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.GONE, ex.getMessage());
  }

  @ExceptionHandler(CodeGenerationException.class)
  public ProblemDetail handleCodeGeneration(CodeGenerationException ex) {
    // Genuinely unexpected at prototype scale; log with stack trace for diagnosis.
    log.error("Short code generation failed", ex);
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.INTERNAL_SERVER_ERROR, "Could not create a short link, please retry");
  }

  /**
   * Last-resort handler: full details go to the log, a generic message to the
   * client — raw exception messages can leak internals.
   *
   * <p>Framework exceptions (404s, 405s, malformed JSON, ...) never land here:
   * Boot's problem-details advice runs at higher precedence, and the
   * ErrorResponse rethrow covers any stragglers so their proper status codes
   * survive this catch-all.
   */
  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception ex) throws Exception {
    if (ex instanceof ErrorResponse) {
      throw ex;
    }
    log.error("Unhandled error while processing request", ex);
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
  }
}

package com.stitchstyle.app.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "Not found", ex.getMessage(), "NOT_FOUND", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<InsufficientStockException.ItemError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toError).toList();
        ProblemDetail problem = ProblemDetailFactory.withErrors(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more fields are invalid.", "VALIDATION_FAILED", errors, request);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(SizeNotSelectedException.class)
    ResponseEntity<ProblemDetail> size(SizeNotSelectedException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Size required", ex.getMessage(), "SIZE_REQUIRED", request);
    }

    @ExceptionHandler(InvalidPhoneException.class)
    ResponseEntity<ProblemDetail> phone(InvalidPhoneException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Invalid phone", ex.getMessage(), "INVALID_PHONE", request);
    }

    @ExceptionHandler(InsufficientStockException.class)
    ResponseEntity<ProblemDetail> stock(InsufficientStockException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetailFactory.withErrors(HttpStatus.UNPROCESSABLE_ENTITY,
                "Insufficient stock", ex.getMessage(), "INSUFFICIENT_STOCK", ex.getErrors(), request);
        return ResponseEntity.unprocessableEntity().body(problem);
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    ResponseEntity<ProblemDetail> transition(InvalidStatusTransitionException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, "Invalid status transition", ex.getMessage(), "INVALID_STATUS_TRANSITION", request);
    }

    @ExceptionHandler(InvalidReturnException.class)
    ResponseEntity<ProblemDetail> invalidReturn(InvalidReturnException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid return", ex.getMessage(), ex.getCode(), request);
    }

    @ExceptionHandler(InvalidChatException.class)
    ResponseEntity<ProblemDetail> invalidChat(InvalidChatException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Invalid chat message", ex.getMessage(), ex.getCode(), request);
    }

    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> concurrent(org.springframework.dao.OptimisticLockingFailureException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, "Concurrent modification",
                "This record was changed by another request. Reload it and try again.", "CONCURRENT_MODIFICATION", request);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    ResponseEntity<ProblemDetail> duplicate(DuplicateResourceException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, "Duplicate resource", ex.getMessage(), "DUPLICATE_RESOURCE", request);
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ResponseEntity<ProblemDetail> reused(IdempotencyKeyReusedException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency key reused", ex.getMessage(), "IDEMPOTENCY_KEY_REUSED", request);
    }

    @ExceptionHandler(IdempotencyInProgressException.class)
    ResponseEntity<ProblemDetail> inProgress(IdempotencyInProgressException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, "Idempotency request in progress", ex.getMessage(), "IDEMPOTENCY_IN_PROGRESS", request);
    }

    @ExceptionHandler(MissingIdempotencyKeyException.class)
    ResponseEntity<ProblemDetail> missingKey(MissingIdempotencyKeyException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Idempotency key required", ex.getMessage(), "IDEMPOTENCY_KEY_REQUIRED", request);
    }

    @ExceptionHandler(com.stitchstyle.app.service.CouponService.InvalidCouponException.class)
    ResponseEntity<ProblemDetail> coupon(com.stitchstyle.app.service.CouponService.InvalidCouponException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Invalid coupon", ex.getMessage(), "INVALID_COUPON", request);
    }

    @ExceptionHandler(MediaValidationException.class)
    ResponseEntity<ProblemDetail> media(MediaValidationException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Invalid media", ex.getMessage(), "INVALID_MEDIA", request);
    }

    @ExceptionHandler(PaymentVerificationException.class)
    ResponseEntity<ProblemDetail> payment(PaymentVerificationException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Payment verification failed", ex.getMessage(), "PAYMENT_VERIFICATION_FAILED", request);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ProblemDetail> rate(RateLimitExceededException ex, HttpServletRequest request) {
        return respond(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded", ex.getMessage(), "RATE_LIMITED", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> auth(AuthenticationException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, "Unauthenticated", "Sign in is required.", "UNAUTHENTICATED", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> forbidden(AccessDeniedException ex, HttpServletRequest request) {
        return respond(HttpStatus.FORBIDDEN, "Forbidden", "You do not have access to this resource.", "FORBIDDEN", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled request failure", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error",
                "An unexpected error occurred. Quote the trace id when contacting support.", "INTERNAL_ERROR", request);
    }

    private InsufficientStockException.ItemError toError(FieldError error) {
        return new InsufficientStockException.ItemError(error.getField(), error.getDefaultMessage());
    }

    private ResponseEntity<ProblemDetail> respond(HttpStatus status, String title, String detail,
                                                  String code, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ProblemDetailFactory.create(status, title, detail, code, request));
    }
}

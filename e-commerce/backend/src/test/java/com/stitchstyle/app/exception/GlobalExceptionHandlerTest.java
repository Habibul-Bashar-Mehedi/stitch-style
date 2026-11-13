package com.stitchstyle.app.exception;

import com.stitchstyle.app.service.CouponService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/public/products");

    @Test
    void notFoundMapsTo404() {
        var response = handler.notFound(new ResourceNotFoundException("missing"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getProperties()).containsEntry("code", "NOT_FOUND");
    }

    @Test
    void validationSizeAndPhoneMapTo400() {
        assertThat(handler.size(new SizeNotSelectedException("pick a size"), request).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.phone(new InvalidPhoneException("bad"), request).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void stockAndReturnMapsTo422() {
        var stock = handler.stock(new InsufficientStockException("stock", List.of(
                new InsufficientStockException.ItemError("items[0].quantity", "Only 1 left"))), request);
        assertThat(stock.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(stock.getBody().getProperties()).containsKey("errors");

        var returned = handler.invalidReturn(new InvalidReturnException("RETURN_X", "bad"), request);
        assertThat(returned.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(returned.getBody().getProperties()).containsEntry("code", "RETURN_X");
    }

    @Test
    void conflictsMapTo409() {
        assertThat(handler.transition(new InvalidStatusTransitionException("bad"), request).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(handler.duplicate(new DuplicateResourceException("dup"), request).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(handler.inProgress(new IdempotencyInProgressException("wait"), request).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(handler.concurrent(new OptimisticLockingFailureException("x"), request).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void idempotencyAndDomainErrorsMapCorrectly() {
        assertThat(handler.reused(new IdempotencyKeyReusedException("reuse"), request).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(handler.missingKey(new MissingIdempotencyKeyException("missing"), request).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.coupon(new CouponService.InvalidCouponException("bad"), request).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.media(new MediaValidationException("bad"), request).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.payment(new PaymentVerificationException("bad"), request).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.invalidChat(new InvalidChatException("CHAT_X", "bad"), request).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.rate(new RateLimitExceededException("slow"), request).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void authenticationMapsTo401AndAccessDeniedTo403() {
        assertThat(handler.auth(new BadCredentialsException("no"), request).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(handler.forbidden(new AccessDeniedException("no"), request).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unexpectedExceptionsBecome500WithoutLeakingTheMessage() {
        var response = handler.unexpected(new IllegalStateException("secret details"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getDetail()).doesNotContain("secret details");
        assertThat(response.getBody().getProperties()).containsEntry("code", "INTERNAL_ERROR");
    }

    @Test
    void problemDetailFactorySetsTraceIdWhenMdcIsPresent() {
        org.slf4j.MDC.put("correlationId", "trace-9");
        try {
            ProblemDetail problem = ProblemDetailFactory.create(HttpStatus.BAD_REQUEST, "Bad", "detail",
                    "SOME_CODE", request);
            assertThat(problem.getType().toString()).isEqualTo("https://stitchandstyle.bd/problems/some-code");
            assertThat(problem.getInstance().toString()).isEqualTo("/api/v1/public/products");
            assertThat(problem.getProperties()).containsEntry("traceId", "trace-9");
        } finally {
            org.slf4j.MDC.remove("correlationId");
        }
    }
}

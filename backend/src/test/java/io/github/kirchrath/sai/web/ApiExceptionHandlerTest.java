package io.github.kirchrath.sai.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsExceptionsToProblemDetails() {
        assertThat(handler.notFound(new NotFoundException("weg")))
                .satisfies(p -> assertThat(p.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value()))
                .satisfies(p -> assertThat(p.getDetail()).isEqualTo("weg"));
        assertThat(handler.badRequest(new IllegalArgumentException("falsch")).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(handler.unavailable(new IllegalStateException("offline")))
                .satisfies(p -> assertThat(p.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value()))
                .satisfies(p -> assertThat(p.getDetail()).isEqualTo("offline"));
        assertThat(handler.conflict(new org.springframework.dao.OptimisticLockingFailureException("alt")).getStatus())
                .isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(handler.tooLarge(new MaxUploadSizeExceededException(10)).getStatus())
                .isEqualTo(HttpStatus.CONTENT_TOO_LARGE.value());
    }
}

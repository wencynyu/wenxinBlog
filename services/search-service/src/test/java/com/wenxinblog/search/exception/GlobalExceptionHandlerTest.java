package com.wenxinblog.search.exception;

import com.wenxinblog.search.dto.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    void handleBadRequest_ShouldReturn400WithErrorMessage() {
        IllegalArgumentException exception = new IllegalArgumentException("Invalid parameter");

        Mono<ResponseEntity<Result<Void>>> response = exceptionHandler.handleBadRequest(exception);

        StepVerifier.create(response)
                .expectNextMatches(entity ->
                        entity.getStatusCode() == HttpStatus.BAD_REQUEST &&
                                entity.getBody() != null &&
                                entity.getBody().getCode() == -1 &&
                                entity.getBody().getMessage().equals("Invalid parameter"))
                .verifyComplete();
    }

    @Test
    void handleRuntime_ShouldReturn500WithGenericError() {
        RuntimeException exception = new RuntimeException("Something went wrong");

        Mono<ResponseEntity<Result<Void>>> response = exceptionHandler.handleRuntime(exception);

        StepVerifier.create(response)
                .expectNextMatches(entity ->
                        entity.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR &&
                                entity.getBody() != null &&
                                entity.getBody().getCode() == -1 &&
                                entity.getBody().getMessage().equals("Internal server error"))
                .verifyComplete();
    }

    @Test
    void handleGeneral_ShouldReturn500WithGenericError() {
        Exception exception = new Exception("Unexpected error");

        Mono<ResponseEntity<Result<Void>>> response = exceptionHandler.handleGeneral(exception);

        StepVerifier.create(response)
                .expectNextMatches(entity ->
                        entity.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR &&
                                entity.getBody() != null &&
                                entity.getBody().getCode() == -1 &&
                                entity.getBody().getMessage().equals("Internal server error"))
                .verifyComplete();
    }

    @Test
    void handleBadRequest_WithNullMessage_ShouldHandleGracefully() {
        IllegalArgumentException exception = new IllegalArgumentException((String) null);

        Mono<ResponseEntity<Result<Void>>> response = exceptionHandler.handleBadRequest(exception);

        StepVerifier.create(response)
                .expectNextMatches(entity ->
                        entity.getStatusCode() == HttpStatus.BAD_REQUEST &&
                                entity.getBody() != null)
                .verifyComplete();
    }

    @Test
    void handleRuntime_WithNestedException_ShouldReturn500() {
        RuntimeException exception = new RuntimeException("Outer error", new RuntimeException("Inner error"));

        Mono<ResponseEntity<Result<Void>>> response = exceptionHandler.handleRuntime(exception);

        StepVerifier.create(response)
                .expectNextMatches(entity ->
                        entity.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR &&
                                entity.getBody() != null)
                .verifyComplete();
    }

    @Test
    void handleGeneral_WithGenericException_ShouldReturn500() {
        Exception exception = new Exception("Generic error");

        Mono<ResponseEntity<Result<Void>>> response = exceptionHandler.handleGeneral(exception);

        StepVerifier.create(response)
                .expectNextMatches(entity ->
                        entity.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR)
                .verifyComplete();
    }
}

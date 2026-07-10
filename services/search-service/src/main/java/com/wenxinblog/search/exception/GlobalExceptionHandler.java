package com.wenxinblog.search.exception;

import com.wenxinblog.search.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<Result<Void>>> handleBadRequest(IllegalArgumentException e) {
        return Mono.just(ResponseEntity.badRequest().body(Result.error(e.getMessage())));
    }

    @ExceptionHandler(RuntimeException.class)
    public Mono<ResponseEntity<Result<Void>>> handleRuntime(RuntimeException e) {
        log.error("Runtime error: {}", e.getMessage(), e);
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error("Internal server error")));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<Result<Void>>> handleGeneral(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error("Internal server error")));
    }
}

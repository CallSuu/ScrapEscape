package com.scrapescape.game;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log=LoggerFactory.getLogger(ApiExceptionHandler.class);
    public record ErrorBody(String code,String message) {}
    @ExceptionHandler(GameException.class)
    ResponseEntity<ErrorBody> gameError(GameException e) { return ResponseEntity.status(e.status).body(new ErrorBody(e.code,e.getMessage())); }
    @ExceptionHandler({MethodArgumentNotValidException.class,MethodArgumentTypeMismatchException.class,HttpMessageNotReadableException.class})
    ResponseEntity<ErrorBody> invalid(Exception e) { return ResponseEntity.badRequest().body(new ErrorBody("INVALID_REQUEST","요청 값과 필수 항목을 확인하세요.")); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> unexpected(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse error) {
            return ResponseEntity.status(error.getStatusCode()).headers(error.getHeaders())
                .body(new ErrorBody("HTTP_ERROR", "요청 주소, 메서드와 Content-Type을 확인하세요."));
        }
        log.error("게임 처리 중 오류",e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorBody("INTERNAL_ERROR","처리 중 오류가 발생했습니다. 다시 시도하세요."));
    }
}

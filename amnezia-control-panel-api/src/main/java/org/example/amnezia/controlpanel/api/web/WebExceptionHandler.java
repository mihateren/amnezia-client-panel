package org.example.amnezia.controlpanel.api.web;

import lombok.extern.slf4j.Slf4j;
import org.example.amnezia.controlpanel.api.constants.Message;
import org.example.amnezia.controlpanel.api.dto.Result;
import org.example.amnezia.controlpanel.api.exception.AppLogicException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class WebExceptionHandler {

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> errors(MethodArgumentNotValidException e) {
        log.warn("Validation error: {}", e.getMessage(), e);
        String validationErrors = e.getBindingResult().getAllErrors().stream()
                .map(ObjectError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return Result.emptyResult(Message.VALIDATION_ERROR.getCode(), validationErrors);
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> errors(HttpMessageNotReadableException e) {
        log.warn("Invalid json: {}", e.getMessage(), e);
        return Result.errorResult(Message.INVALID_JSON);
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> errors(AppLogicException e) {
        log.warn("Application logic error: code = {}, message = {}", e.getCode(), e.getMessage(), e);
        return Result.emptyResult(e.getCode(), e.getMessage());
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> errors(Exception e) {
        log.error("Internal error", e);
        return Result.errorResult(Message.INTERNAL_ERROR);
    }
}

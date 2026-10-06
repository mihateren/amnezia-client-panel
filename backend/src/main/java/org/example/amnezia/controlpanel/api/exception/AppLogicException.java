package org.example.amnezia.controlpanel.api.exception;

import lombok.Getter;

public class AppLogicException extends RuntimeException {

    @Getter
    private int code;

    public AppLogicException(int code, String message) {
        this.code = code;
        super(message);
    }

    public AppLogicException(int code, String message, Throwable cause) {
        this.code = code;
        super(message, cause);
    }
}

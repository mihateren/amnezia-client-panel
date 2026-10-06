package org.example.amnezia.controlpanel.api.constants;

import lombok.Getter;

@Getter
public enum Message {

    RESULT_SUCCESS(0, "Ok"),
    VALIDATION_ERROR(-100, "Ошибка валидации: %s"),
    INVALID_JSON(-200, "Невалидный JSON"),
    INTERNAL_ERROR(-300, "Внутренняя ошибка"),
    AUTHORIZATION_FAILED(-1920, "Авторизация закончилась с ошибкой"),
    AUTH_TOKEN_NOT_RECEIVED(-1921, "Авторизационный токен не был получен"),
            ;

    private final int code;
    private final String statusMessage;

    Message(int code, String statusMessage) {
        this.code = code;
        this.statusMessage = statusMessage;
    }
}

package org.example.amnezia.controlpanel.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.example.amnezia.controlpanel.api.constants.Message;

import static org.example.amnezia.controlpanel.api.constants.Message.RESULT_SUCCESS;

@Data
@Accessors(chain = true)
public class Result<T> {

    @JsonProperty("payload")
    @Schema(description = "Данные")
    private T payload;

    @JsonProperty(value = "result_code", required = true)
    @Schema(description = "Код ответа")
    private int resultCode = 0;

    @JsonProperty("message")
    @Schema(description = "Сообщение", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message = "";


    public static <T> Result<T> emptyResult(int code, String message) {
        return new Result<T>().setResultCode(code).setMessage(message);
    }


    public static <T> Result<T> payloadResult(int code, String message, T payload) {
        return new Result<T>().setResultCode(code).setMessage(message).setPayload(payload);
    }

    public static <T> Result<T> successResult() {
        return new Result<T>()
                .setResultCode(RESULT_SUCCESS.getCode())
                .setMessage(RESULT_SUCCESS.getStatusMessage());
    }

    public static <T> Result<T> successResult(T payload) {
        return new Result<T>()
                .setResultCode(RESULT_SUCCESS.getCode())
                .setMessage(RESULT_SUCCESS.getStatusMessage()).setPayload(payload);
    }

    public static <T> Result<T> errorResult(Message error) {
        return new Result<T>()
                .setResultCode(error.getCode()).setMessage(error.getStatusMessage());
    }

    public static <T> Result<T> errorResult(Message error, T payload) {
        return new Result<T>()
                .setResultCode(error.getCode())
                .setMessage(error.getStatusMessage())
                .setPayload(payload);
    }
}

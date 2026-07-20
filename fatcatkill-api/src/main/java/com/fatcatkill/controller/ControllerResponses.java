package com.fatcatkill.controller;

import com.fatcatkill.model.MessagePayload;
import com.fatcatkill.model.MessagePayloadException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.util.Map;

final class ControllerResponses {
    private ControllerResponses() {}

    static ResponseEntity<?> badRequest(Exception exception) {
        if (exception instanceof MessagePayloadException localized) return badRequest(localized.getMessagePayload());
        String reason = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        return badRequest(MessagePayload.of("backend.error.badRequest", Map.of("reason", reason), reason));
    }

    static ResponseEntity<?> badRequest(MessagePayload message) {
        return status(org.springframework.http.HttpStatus.BAD_REQUEST, message);
    }

    static ResponseEntity<?> status(HttpStatusCode status, MessagePayload message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }
}

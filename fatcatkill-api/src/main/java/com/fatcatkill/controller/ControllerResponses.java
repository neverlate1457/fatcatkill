package com.fatcatkill.controller;

import com.fatcatkill.model.MessagePayload;
import com.fatcatkill.model.MessagePayloadException;
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
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }
}

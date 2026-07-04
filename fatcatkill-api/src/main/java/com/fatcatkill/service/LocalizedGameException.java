package com.fatcatkill.service;

import com.fatcatkill.model.MessagePayload;
import com.fatcatkill.model.MessagePayloadException;

public class LocalizedGameException extends RuntimeException implements MessagePayloadException {
    private final MessagePayload messagePayload;

    public LocalizedGameException(MessagePayload messagePayload) {
        super(messagePayload == null ? null : messagePayload.getFallback());
        this.messagePayload = messagePayload;
    }

    @Override
    public MessagePayload getMessagePayload() {
        return messagePayload;
    }
}